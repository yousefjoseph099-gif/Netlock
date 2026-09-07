package com.netlock.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.netlock.app.data.AppDatabase
import com.netlock.app.data.BlockMode
import com.netlock.app.data.ListType
import com.netlock.app.data.Prefs
import com.netlock.app.util.DnsPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FilterVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.netlock.app.action.STOP"
        const val LOCAL_IP = "10.111.222.2"
        const val DNS_IP = "10.111.222.1"
        const val UPSTREAM_DNS_1 = "1.1.1.1"
        const val UPSTREAM_DNS_2 = "8.8.8.8"
        private const val CHANNEL_ID = "netlock_channel"
        private const val NOTIF_ID = 42
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var readerThread: Thread? = null
    private val workerPool = Executors.newFixedThreadPool(4)

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // In-memory cache of the active lists, kept up to date via Room Flow collection.
    @Volatile private var whitelistCache: Set<String> = emptySet()
    @Volatile private var blacklistCache: Set<String> = emptySet()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfAndVpn()
            return START_NOT_STICKY
        }

        if (running.get()) {
            return START_STICKY
        }

        val prefs = Prefs.getInstance(this)
        val selectedPackages = prefs.selectedPackages

        if (selectedPackages.isEmpty()) {
            // Refuse to start with no target apps chosen - we never want to
            // silently fall back to filtering the entire device.
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())
        observeLists()
        establishVpn(selectedPackages)
        running.set(true)
        prefs.vpnRunning = true
        return START_STICKY
    }

    private fun observeLists() {
        val dao = AppDatabase.getInstance(this).domainDao()
        scope.launch {
            dao.observeByType(ListType.WHITELIST).collect { list ->
                whitelistCache = list.map { it.domain }.toSet()
            }
        }
        scope.launch {
            dao.observeByType(ListType.BLACKLIST).collect { list ->
                blacklistCache = list.map { it.domain }.toSet()
            }
        }
    }

    private fun establishVpn(selectedPackages: Set<String>) {
        val builder = Builder()
            .setSession("NetLock")
            .addAddress(LOCAL_IP, 32)
            .addDnsServer(DNS_IP)
            .addRoute(DNS_IP, 32)
            .setMtu(1500)
            .setBlocking(true)

        var addedAny = false
        for (pkg in selectedPackages) {
            try {
                builder.addAllowedApplication(pkg)
                addedAny = true
            } catch (e: Exception) {
                // App may have been uninstalled since being selected; skip it.
            }
        }

        if (!addedAny) {
            stopSelf()
            return
        }

        vpnInterface = try {
            builder.establish()
        } catch (e: Exception) {
            null
        }
        val iface = vpnInterface ?: run {
            stopSelf()
            return
        }

        readerThread = Thread { runReadLoop(iface) }.also { it.isDaemon = true; it.start() }
    }

    private fun runReadLoop(iface: ParcelFileDescriptor) {
        val input = FileInputStream(iface.fileDescriptor)
        val buffer = ByteArray(32767)

        try {
            while (running.get() || vpnInterface != null) {
                val length = input.read(buffer)
                if (length <= 0) continue

                val packetCopy = buffer.copyOf(length)
                workerPool.submit { handlePacket(packetCopy) }
            }
        } catch (e: Exception) {
            // Interface closed / service stopping - expected on shutdown.
        }
    }

    private fun handlePacket(packet: ByteArray) {
        val query = DnsPacket.tryParseDnsQuery(packet, packet.size) ?: return
        val iface = vpnInterface ?: return

        val prefs = Prefs.getInstance(this)
        val hostname = query.questionName.lowercase()
        val blocked = isBlocked(hostname, prefs.mode)

        val output = FileOutputStream(iface.fileDescriptor)

        if (blocked) {
            val response = DnsPacket.buildBlockedDnsResponse(query.dnsPayload)
            val responsePacket = DnsPacket.buildResponsePacket(
                InetAddress.getByName(DNS_IP), query.srcIp, query.srcPort, response
            )
            synchronized(this) { output.write(responsePacket) }
            return
        }

        // Allowed: relay the real query to a genuine upstream DNS resolver and
        // pipe the real answer back through the tunnel. Try the primary resolver
        // first, falling back to the secondary one if it doesn't answer in time.
        val dnsAnswer = forwardToUpstream(query.dnsPayload, UPSTREAM_DNS_1)
            ?: forwardToUpstream(query.dnsPayload, UPSTREAM_DNS_2)

        if (dnsAnswer != null) {
            val responsePacket = DnsPacket.buildResponsePacket(
                InetAddress.getByName(DNS_IP), query.srcIp, query.srcPort, dnsAnswer
            )
            synchronized(this) { output.write(responsePacket) }
        }
        // If both upstreams failed (network hiccup), we just drop this one query -
        // the browser/app will retry on its own.
    }

    private fun forwardToUpstream(dnsQuery: ByteArray, upstreamIp: String): ByteArray? {
        return try {
            val socket = DatagramSocket()
            protect(socket) // exempt this socket from the VPN so it doesn't loop back in
            socket.soTimeout = 3000

            val upstream = InetAddress.getByName(upstreamIp)
            socket.send(DatagramPacket(dnsQuery, dnsQuery.size, upstream, 53))

            val respBuf = ByteArray(4096)
            val respPacket = DatagramPacket(respBuf, respBuf.size)
            socket.receive(respPacket)
            socket.close()

            respBuf.copyOf(respPacket.length)
        } catch (e: Exception) {
            null
        }
    }

    private fun isBlocked(hostname: String, mode: BlockMode): Boolean {
        return when (mode) {
            BlockMode.WHITELIST -> !matchesAny(hostname, whitelistCache)
            BlockMode.BLACKLIST -> matchesAny(hostname, blacklistCache)
        }
    }

    /** True if hostname equals an entry, or is a subdomain of one (e.g. "m.example.com" matches "example.com"). */
    private fun matchesAny(hostname: String, entries: Set<String>): Boolean {
        if (entries.isEmpty()) return false
        if (entries.contains(hostname)) return true
        return entries.any { entry -> hostname.endsWith(".$entry") }
    }

    private fun stopSelfAndVpn() {
        running.set(false)
        Prefs.getInstance(this).vpnRunning = false
        try { vpnInterface?.close() } catch (e: Exception) { /* ignore */ }
        vpnInterface = null
        readerThread = null
        job.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfAndVpn()
        workerPool.shutdownNow()
        super.onDestroy()
    }

    override fun onRevoke() {
        // The system tore down our tunnel - either the user disconnected it from
        // Settings > Network > VPN, or another VPN app took over the single VPN slot.
        //
        // If no password is set, the user hasn't asked for tamper-resistance, so we
        // respect it and stop cleanly. If a password IS set, we treat this the same
        // as someone trying to bypass the lock without entering it: reconnect right
        // away using the same app selection, rather than silently staying off.
        val prefs = Prefs.getInstance(this)
        val selectedPackages = prefs.selectedPackages

        if (!prefs.hasPassword() || selectedPackages.isEmpty()) {
            stopSelfAndVpn()
            super.onRevoke()
            return
        }

        try { vpnInterface?.close() } catch (e: Exception) { /* ignore */ }
        vpnInterface = null

        // A short delay avoids hammering establish() in a tight loop if the
        // permission really was fully revoked and re-establishing keeps failing.
        android.os.Handler(mainLooper).postDelayed({
            establishVpn(selectedPackages)
        }, 500)

        super.onRevoke()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetLock protection is active")
            .setContentText("Open the app and enter your password to change settings or stop.")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }
}
