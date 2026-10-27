# Google Play Console checklist: iperf3KMP 1.0.1

Working list for getting 1.0.1 into Play internal testing. Answers below were settled on
2026-10-26; tick items off as you go.

**Starting point**
- Code: commit `ba7c7f3` on `test-1.0.1` (not pushed yet).
- Signed bundle:
  `androidApp/build/outputs/bundle/release/androidApp-release.aab`, version 1.0.1 (10001).
- Upload key: `~/keys/iperf3kmp-upload-2.jks`, alias `upload`, valid until 2054-03-13.
  - SHA-256 fingerprint:
    `47:1B:8B:63:34:0C:46:F5:3E:DD:84:E8:12:C0:01:4F:9B:C5:25:0B:47:14:08:01:F4:B8:14:8F:EE:6E:3E:2A`
- The existing Play app record is locked to the wrong package (`com.abramson.iperf3kmp`). A package
  name can't be changed once used, so a **new app record** is needed.

## 0. Before starting
- [X] Back up `~/keys/iperf3kmp-upload-2.jks` and its password (password manager). Losing them means
      a Play support request to reset the upload key.
- [X] Delete the old, unregistered `~/keys/iperf3kmp-upload.jks` (expires 2029; Play rejected it).
- [X] If the code changed since `ba7c7f3`: commit, then rebuild with
```
 git diff --name-only  ba7c7f3
docs/platform-builds/TEST_MATRIX.md
docs/store/PLAY_CONSOLE_CHECKLIST.md
 ```
      `./gradlew :androidApp:bundleRelease` and check `jarsigner -verify` says `jar verified.`

## 1. Create the new app
- [X] All apps → **Create app**.
- [X] If asked for a package name, enter exactly: `com.jabramson.iperf3_network_tester_kmp`.
- [X] App name: `iperf3KMP` (or `iperf3KMP Network Tester`, max 30 characters). Don't imply
      endorsement by ESnet/LBNL (iperf3 license clause 3).
- [X] App or game: **App**. Free or paid: as planned (free can't later become paid).

## 2. App content declarations (Policy → App content)
- [X] **Target audience:** 18 and over only. Leave every under-13 group unchecked (avoids the
      Families policy).
- [X] "Restrict users Google has determined to be minors": **off**.
- [X] Could the app unintentionally appeal to children: **No**.
- [X] **Data safety:** collects or shares user data: **No**. The app has no analytics, crash
      reporting or ads libraries, saved results stay on the device, and its only permissions are
      `INTERNET` and `ACCESS_NETWORK_STATE`. Revisit if any SDK is ever added.
- [X] **Advertising ID:** **No**. The merged release manifest has no `AD_ID` permission.
- [X] **Financial features:** "My app doesn't provide any financial features".
- [X] Any other declarations the Console lists (e.g. privacy policy URL, ads, content rating
      questionnaire): answer for a network-testing utility with no ads, no accounts and no
      user-generated content.

## 3. Store listing
- [X] **Category:** Tools.
- [X] **Tags:** Wi-Fi, Network connectivity (both in the Tools group).
- [X] **External marketing:** on (default).
- [X] **App icon:** `desktopApp/src/main/resources/app-icon.png` (512×512, 32-bit PNG).
      Known flaws for later: white corner specks and "NETWORK TEST" cut off at the bottom.
- [X] **Feature graphic:** `docs/store/play-feature-graphic.png` (1024×500, no alpha).
- [X] **Phone screenshots (at least 2):** the captures in `docs/store/screen_grab*.png` are
      1220×2712 (2.22:1). Play allows at most 2:1, so pad them to 1356×2712 (or 2712×1356) first.
- [X] Short description (max 80 characters) and full description (max 4000).

## 4. Internal testing release
- [X] Test and release → Testing → **Internal testing**.
- [X] **Testers:** create an email list with your Google account.
- [X] **Create new release.** Accept **Play App Signing** (Google holds the app signing key; yours
      is only the upload key).
- [X] Upload `androidApp-release.aab`. It should show **1.0.1 (10001)**.
  - Expected, non-blocking warning: "There is no deobfuscation file associated with this App
    Bundle." Release builds set `isMinifyEnabled = false` (`androidApp/build.gradle.kts`), so R8
    doesn't run, there's no `mapping.txt`, and Play crash traces are already readable. Don't turn
    on R8 just to clear it: the JNI bridges (`NativeIperfBridge`, `NativeFormatterBridge`) and the
    `IperfCallback` methods C calls back into are looked up by name, so renamed or stripped classes
    fail at runtime (`UnsatisfiedLinkError`/`NoSuchMethodError`), not at build time. Enabling it
    needs keep rules plus a full device retest; leave it for a later release.
  - Possible, also non-blocking: "This App Bundle contains native code, and you've not uploaded
    debug symbols." Without them, crashes inside the native libraries (iperf3, native formatting)
    show as raw addresses. To fix later, add `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` to the
    `release` build type; it doesn't change the shipped code, but it is a rebuild (needs a new
    version code).
- [*] **Release name:** `1.0.1 (10001)`.
   = Automatically populated as: **10001 (1.0.1)**
- [X] **Release notes:**
      ```
      <en-US>
      First Google Play test release of iperf3KMP, a network bandwidth tester built on iperf3 3.21.

      New in 1.0.1:
      • Fixed a crash when Run/Stop was tapped rapidly against a server that doesn't respond
      • A new run now says when it is waiting for the previous one to finish
      • Stopping a test no longer shows an "Uncaught Exception" message
      • Results and the Run button reset promptly after a failed connection
      </en-US>
      ```
- [X] Save → Review release → **Start rollout**. (Console button: "Save and publish".)
- [X] App integrity → App signing: upload key fingerprint matches `47:1B:8B:63:…:6E:3E:2A`.
      Confirmed 2026-10-26 (full SHA-256 match). The same page's **App signing key** (Google-held,
      signs every install) is different by design. Give it to any service that checks the
      installed app's signature:
      - SHA-1 `91:B5:EA:74:B2:C5:91:23:4E:6C:4E:9E:A4:17:25:26:D7:18:55:54`
      - SHA-256 `F5:59:99:0A:20:80:39:52:B1:E6:F9:1D:23:C2:93:3D:FB:6F:A1:C1:0A:DE:DC:50:65:01:24:DA:E4:20:12:11`

## 5. Install and smoke-test from Play
- [X] **Uninstall the debug build first.** It's signed with a different key, so Play can't install
      over it. This also clears that build's History.
- [X] Open the tester opt-in link (Testers tab) on the phone, accept, install:
      https://play.google.com/apps/internaltest/4701134935371941254 (works only for accounts on
      the internal testers list).
- [X] Smoke test:
  - [X] a normal test against a real server, on Wi-Fi and on cellular
  - [X] Run/Stop spam against `1.1.1.1`: no crash, "⏳ Waiting to start test…" appears
  - [X] Save → History, delete a row, force stop and reopen
- [X] Record the result in `docs/platform-builds/TEST_MATRIX.md` section G (Android row + results log, 2026-10-26).

## 6. Clean up and finish 1.0.1
- [X] Delete the old draft app tied to `com.abramson.iperf3kmp`: Settings → Advanced settings →
      Delete app (allowed only if it was never published).
- [ ] Once Play and TestFlight both accept 1.0.1: push to GitHub and tag `1.0.1` on the commit the
      store builds came from. They came from two commits: **iOS TestFlight build from `37e14cf`;
      Android Play build from `ba7c7f3`**. `ba7c7f3` adds only Android release signing (plus docs and
      store images) on top of `37e14cf`, but that change produced a new Android artifact, so tag
      `ba7c7f3` and name both commits in the tag message:
      ```
      git tag -a 1.0.1 ba7c7f3 -m "1.0.1: iOS TestFlight build from 37e14cf; Android Play build from ba7c7f3"
      git push origin 1.0.1
      ```
- [ ] Any fix after the upload needs a version bump (1.0.2): Play and App Store Connect reject a
      repeated build number (10001).
