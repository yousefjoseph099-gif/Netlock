# NetLock

A self-control / parental-control style Android app that blocks internet access
to everything except a chosen list of sites (whitelist mode), or blocks a chosen
list of sites while allowing everything else (blacklist mode) - like Cold Turkey,
but for Android, and scoped **only to the apps you pick** (normally your browser(s)).
Every other app on the device keeps completely normal, unfiltered internet access.

## What it does

- **Whitelist mode**: only the domains you add are reachable from the selected app(s); everything else is blocked.
- **Blacklist mode**: only the domains you add are blocked; everything else works normally.
- **Bulk import**: add domains one at a time, or import a `.txt`/`.csv` file (one domain per line, or comma-separated).
- **Password lock**: once a password is set, starting/stopping protection, editing the lists, changing mode, and picking filtered apps all require it.
- **App-scoped filtering**: you choose which installed apps (e.g. Chrome, Firefox) are filtered. Everything else you haven't selected is left alone - the filter never touches apps you didn't pick.

## How it works (technical summary)

Android apps can't intercept another app's network traffic without root, but they
*can* create a local `VpnService` that the OS routes traffic through. NetLock uses
that API in a narrow way:

1. `VpnService.Builder.addAllowedApplication()` restricts the virtual VPN interface
   to **only** the app package(s) you selected. Every other app bypasses the VPN
   entirely and talks to the network directly, untouched.
2. Instead of routing *all* traffic (which would require re-implementing a full
   TCP/IP stack), NetLock only routes DNS traffic through the tunnel: it adds a
   route just for a fake local DNS server address and tells Android to use that
   as the DNS server for the selected app(s). Actual page/data traffic still goes
   directly over the real network - it's never touched by NetLock.
3. Every DNS query from the selected app(s) is intercepted, checked against your
   whitelist/blacklist, and either:
   - **relayed** untouched to a real public DNS resolver (1.1.1.1) and the real
     answer sent back, or
   - **blocked** with a synthetic "domain does not exist" (NXDOMAIN) response.

This is the same general technique used by well-known open-source Android
DNS-filtering apps (e.g. DNS66, PersonalDNSFilter).

### Honest limitations

- **This is DNS-level filtering**, not deep packet inspection. It's effective for
  normal browsing, but:
  - If the selected browser has its own "Secure DNS / DNS-over-HTTPS" setting
    turned on and pointed at a provider that ignores the system DNS server, it
    can bypass the filter. Turn that browser setting off (use "system default")
    for NetLock to work reliably.
  - A site that hardcodes a raw IP address (very rare in normal browsing) would
    not be caught, since there's no domain lookup to intercept.
- **Uninstall protection is not implemented.** Android does not let a normal app
  prevent its own uninstallation. A determined user could uninstall NetLock the
  same way as any app. (A `DeviceAdminReceiver` could add friction here in a
  future version, but that requires an intrusive permission and was intentionally
  left out of this build to keep the app simple and low-permission.)
- Only one whitelist/blacklist is checked at a time, per the mode you pick.

## Project structure

```
NetLock/
  app/src/main/java/com/netlock/app/
    MainActivity.kt          - main screen (mode, lists, apps, password, start/stop)
    FilterVpnService.kt       - the VpnService that does the actual DNS filtering
    UnlockActivity.kt         - password prompt gate
    SetupPasswordActivity.kt  - set/change/remove password
    AppPickerActivity.kt      - pick which installed apps get filtered
    data/                     - Room database (domain lists) + encrypted settings store
    util/                     - DNS packet parsing/building, domain import, password hashing
  .github/workflows/android-build.yml  - CI that builds the APK automatically
```

## Building it yourself

### Option A: GitHub Actions (recommended - matches what you asked for)

1. Create a new GitHub repository and push this entire folder to it.
2. Go to the **Actions** tab in your repo - a workflow called **"Build Android APK"**
   will run automatically on every push (or trigger it manually with "Run workflow").
3. When it finishes, open the run and download the **`netlock-debug-apk`** artifact
   under "Artifacts" at the bottom of the run page. Unzip it to get `app-debug.apk`.
4. Copy that APK to your Android phone and install it (you'll need to allow
   "install unknown apps" for whichever app you use to open it).

No local Android Studio setup is required for this path - GitHub's runners have
everything needed (JDK, Android SDK, Gradle) and the workflow installs the exact
versions used.

### Option B: Android Studio (local)

1. Open the `NetLock` folder in Android Studio (Giraffe or newer).
2. Let it sync Gradle (it will fetch the same dependencies as CI).
3. Run on a device/emulator, or Build > Build Bundle(s)/APK(s) > Build APK(s).

## Using the app

1. Open NetLock, tap **"Choose apps (browsers)"** and select the browser(s) you
   want filtered (only those apps will ever be affected).
2. Pick a mode: **Whitelist only** or **Blacklist**.
3. Add domains one at a time, or tap **"Import from .txt / .csv"** to bulk-add
   a list (one domain per line, or comma-separated - `https://`, `www.`, and
   paths are stripped automatically).
4. Optionally tap **"Set / change password"** so protection can't be turned off
   or the lists edited without it.
5. Tap **"Start Protection"** and accept the one-time Android VPN permission
   prompt (this is the standard OS dialog required for any local VPN/filtering
   app - NetLock does not send your traffic to any external server).

## A note on lawful use

This is meant for self-control (blocking your own distracting sites) or for
parental control on a device you own or manage. If you plan to install it on a
device belonging to someone else (an employee, another family member's phone,
etc.), make sure you have their knowledge/consent as required by the laws that
apply to you.
