package com.netlock.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.netlock.app.data.Prefs

/**
 * Being an *active* device administrator is what makes Android refuse a plain
 * uninstall ("This app can't be uninstalled while it's a device admin") - the
 * user has to deactivate admin first in Settings > Security > Device admin
 * apps, then uninstall as a second, separate step. That's the entire effect
 * this buys us.
 *
 * IMPORTANT / honest limitation: the "Deactivate" button the user taps lives
 * inside Android's own Settings screen. There is no API that lets any app -
 * including this one - put a password prompt in front of that system button,
 * or refuse the deactivation outright. [onDisableRequested] can only supply a
 * short warning string that Android shows before the system dialog; it cannot
 * gate the action on anything. Once the user actually confirms deactivation,
 * it happens - full stop.
 *
 * What we *can* do is react immediately afterward: [onDisabled] fires right
 * when deactivation completes, so we flag it and, if a NetLock password is
 * set, the app will demand that same password again before anything else can
 * be done in it the next time it's opened (see Prefs.adminTamperFlag /
 * MainActivity's onResume check). This does not block the uninstall that can
 * now follow - nothing on a non-managed device can - it just makes sure
 * turning off tamper-protection doesn't silently succeed without your
 * password being asked for again.
 */
class NetLockDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Prefs.getInstance(context).deviceAdminActive = true
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // This string is shown by the OS on the system confirmation screen,
        // before the user taps the final "Deactivate this device admin app"
        // button. We cannot require the password here - Android gives us a
        // warning message, not a gate.
        return context.getString(R.string.device_admin_disable_warning)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        val prefs = Prefs.getInstance(context)
        prefs.deviceAdminActive = false
        // Only meaningful if the user actually set a NetLock password; if they
        // never did, there's nothing to re-confirm.
        if (prefs.hasPassword()) {
            prefs.adminTamperFlag = true
        }
    }
}
