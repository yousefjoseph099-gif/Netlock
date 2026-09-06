package com.netlock.app.util

import java.io.ByteArrayOutputStream
import java.net.InetAddress

/** A parsed IPv4 + UDP + DNS-query packet read from the tun interface. */
data class ParsedDnsQuery(
    val srcIp: InetAddress,
    val srcPort: Int,
    val dstIp: InetAddress,
    val dstPort: Int,
    val dnsPayload: ByteArray,   // raw DNS message bytes (query)
    val questionName: String    // e.g. "www.example.com"
)

object DnsPacket {

    private const val PROTOCOL_UDP = 17

    /**
     * Attempts to parse [length] bytes of [packet] as an IPv4/UDP/DNS query.
     * Returns null for anything else (non-IPv4, non-UDP, not port 53, malformed).
     */
    fun tryParseDnsQuery(packet: ByteArray, length: Int): ParsedDnsQuery? {
        if (length < 28) return null // 20 (min IP) + 8 (UDP) minimum

        val versionAndIhl = packet[0].toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != 4) return null // IPv6 not handled by this simple filter

        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null

        val srcIp = InetAddress.getByAddress(packet.copyOfRange(12, 16))
        val dstIp = InetAddress.getByAddress(packet.copyOfRange(16, 20))

        val udpOffset = ihl
        val srcPort = readUShort(packet, udpOffset)
        val dstPort = readUShort(packet, udpOffset + 2)
        val udpLength = readUShort(packet, udpOffset + 4)

        if (dstPort != 53) return null

        val dnsOffset = udpOffset + 8
        val dnsLength = (udpLength - 8).coerceAtMost(length - dnsOffset)
        if (dnsLength < 12) return null // DNS header is 12 bytes

        val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)

        val qdcount = readUShort(dnsPayload, 4)
        if (qdcount < 1) return null

        val name = try {
            readQName(dnsPayload, 12).first
        } catch (e: Exception) {
            return null
        }

        return ParsedDnsQuery(srcIp, srcPort, dstIp, dstPort, dnsPayload, name)
    }

    /** Reads a DNS QNAME starting at [offset], returns (dotted name, offset after name). */
    private fun readQName(data: ByteArray, offset: Int): Pair<String, Int> {
        val labels = mutableListOf<String>()
        var pos = offset
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) {
                pos += 1
                break
            }
            pos += 1
            if (pos + len > data.size) throw IllegalArgumentException("Bad label")
            labels.add(String(data, pos, len, Charsets.US_ASCII))
            pos += len
        }
        return Pair(labels.joinToString("."), pos)
    }

    private fun readUShort(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun writeUShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    /**
     * Wraps [dnsPayload] (a raw DNS message, either a relayed upstream answer or a
     * synthetic blocked response) into a full IPv4 + UDP packet addressed FROM
     * [fromIp]:53 TO [toIp]:[toPort], ready to be written back into the tun interface.
     */
    fun buildResponsePacket(fromIp: InetAddress, toIp: InetAddress, toPort: Int, dnsPayload: ByteArray): ByteArray {
        val udpLength = 8 + dnsPayload.size
        val totalLength = 20 + udpLength

        val packet = ByteArray(totalLength)

        // ---- IPv4 header ----
        packet[0] = 0x45 // version 4, IHL 5 (20 bytes, no options)
        packet[1] = 0    // DSCP/ECN
        packet[2] = ((totalLength shr 8) and 0xFF).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[4] = 0; packet[5] = 0 // identification
        packet[6] = 0x40.toByte() // flags: don't fragment
        packet[7] = 0
        packet[8] = 64 // TTL
        packet[9] = PROTOCOL_UDP.toByte()
        packet[10] = 0; packet[11] = 0 // checksum placeholder

        val fromBytes = fromIp.address
        val toBytes = toIp.address
        System.arraycopy(fromBytes, 0, packet, 12, 4)
        System.arraycopy(toBytes, 0, packet, 16, 4)

        val ipChecksum = checksum(packet, 0, 20)
        packet[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        // ---- UDP header ---- (checksum left as 0: optional for IPv4, valid per RFC 768)
        val udpOffset = 20
        packet[udpOffset] = 0; packet[udpOffset + 1] = 53          // src port 53
        packet[udpOffset + 2] = ((toPort shr 8) and 0xFF).toByte()
        packet[udpOffset + 3] = (toPort and 0xFF).toByte()
        packet[udpOffset + 4] = ((udpLength shr 8) and 0xFF).toByte()
        packet[udpOffset + 5] = (udpLength and 0xFF).toByte()
        packet[udpOffset + 6] = 0; packet[udpOffset + 7] = 0 // checksum = 0 (disabled)

        System.arraycopy(dnsPayload, 0, packet, udpOffset + 8, dnsPayload.size)

        return packet
    }

    /** Builds a synthetic NXDOMAIN DNS response echoing the original query's ID and question. */
    fun buildBlockedDnsResponse(originalQuery: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()

        // ID: copy from original query
        out.write(originalQuery[0].toInt())
        out.write(originalQuery[1].toInt())

        // Flags: 1000 0001 1000 0011 = QR=1 (response), Opcode=0, RD=1, RA=1, RCODE=3 (NXDOMAIN)
        out.write(0x81)
        out.write(0x83)

        // QDCOUNT = 1 (echo the question), ANCOUNT/NSCOUNT/ARCOUNT = 0
        writeUShort(out, 1)
        writeUShort(out, 0)
        writeUShort(out, 0)
        writeUShort(out, 0)

        // Echo the original question section back (name + qtype + qclass)
        val (_, afterName) = readQName(originalQuery, 12)
        val questionEnd = (afterName + 4).coerceAtMost(originalQuery.size)
        out.write(originalQuery, 12, questionEnd - 12)

        return out.toByteArray()
    }

    /** Standard 16-bit one's-complement checksum used by the IPv4 header. */
    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 == 1) {
            sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
