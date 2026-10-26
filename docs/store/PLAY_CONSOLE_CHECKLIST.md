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
- [ ] Back up `~/keys/iperf3kmp-upload-2.jks` and its password (password manager). Losing them means
      a Play support request to reset the upload key.
- [ ] Delete the old, unregistered `~/keys/iperf3kmp-upload.jks` (expires 2029; Play rejected it).
- [ ] If the code changed since `ba7c7f3`: commit, then rebuild with
      `./gradlew :androidApp:bundleRelease` and check `jarsigner -verify` says `jar verified.`

## 1. Create the new app
- [ ] All apps → **Create app**.
- [ ] If asked for a package name, enter exactly: `com.jabramson.iperf3_network_tester_kmp`.
- [ ] App name: `iperf3KMP` (or `iperf3KMP Network Tester`, max 30 characters). Don't imply
      endorsement by ESnet/LBNL (iperf3 license clause 3).
- [ ] App or game: **App**. Free or paid: as planned (free can't later become paid).

## 2. App content declarations (Policy → App content)
- [ ] **Target audience:** 18 and over only. Leave every under-13 group unchecked (avoids the
      Families policy).
- [ ] "Restrict users Google has determined to be minors": **off**.
- [ ] Could the app unintentionally appeal to children: **No**.
- [ ] **Data safety:** collects or shares user data: **No**. The app has no analytics, crash
      reporting or ads libraries, saved results stay on the device, and its only permissions are
      `INTERNET` and `ACCESS_NETWORK_STATE`. Revisit if any SDK is ever added.
- [ ] **Advertising ID:** **No**. The merged release manifest has no `AD_ID` permission.
- [ ] **Financial features:** "My app doesn't provide any financial features".
- [ ] Any other declarations the Console lists (e.g. privacy policy URL, ads, content rating
      questionnaire): answer for a network-testing utility with no ads, no accounts and no
      user-generated content.

## 3. Store listing
- [ ] **Category:** Tools.
- [ ] **Tags:** Wi-Fi, Network connectivity (both in the Tools group).
- [ ] **External marketing:** on (default).
- [ ] **App icon:** `desktopApp/src/main/resources/app-icon.png` (512×512, 32-bit PNG).
      Known flaws for later: white corner specks and "NETWORK TEST" cut off at the bottom.
- [ ] **Feature graphic:** `docs/store/play-feature-graphic.png` (1024×500, no alpha).
- [ ] **Phone screenshots (at least 2):** the captures in `docs/store/screen_grab*.png` are
      1220×2712 (2.22:1). Play allows at most 2:1, so pad them to 1356×2712 (or 2712×1356) first.
- [ ] Short description (max 80 characters) and full description (max 4000).

## 4. Internal testing release
- [ ] Test and release → Testing → **Internal testing**.
- [ ] **Testers:** create an email list with your Google account.
- [ ] **Create new release.** Accept **Play App Signing** (Google holds the app signing key; yours
      is only the upload key).
- [ ] Upload `androidApp-release.aab`. It should show **1.0.1 (10001)**.
- [ ] **Release name:** `1.0.1 (10001)`.
- [ ] **Release notes:**
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
- [ ] Save → Review release → **Start rollout**.
- [ ] App integrity → App signing: upload key fingerprint matches `47:1B:8B:63:…:6E:3E:2A`.

## 5. Install and smoke-test from Play
- [ ] **Uninstall the debug build first.** It's signed with a different key, so Play can't install
      over it. This also clears that build's History.
- [ ] Open the tester opt-in link (Testers tab) on the phone, accept, install.
- [ ] Smoke test:
  - [ ] a normal test against a real server, on Wi-Fi and on cellular
  - [ ] Run/Stop spam against `1.1.1.1`: no crash, "⏳ Waiting to start test…" appears
  - [ ] Save → History, delete a row, force stop and reopen
- [ ] Record the result in `docs/platform-builds/TEST_MATRIX.md` section G.

## 6. Clean up and finish 1.0.1
- [ ] Delete the old draft app tied to `com.abramson.iperf3kmp`: Settings → Advanced settings →
      Delete app (allowed only if it was never published).
- [ ] Once Play and TestFlight both accept 1.0.1: push to GitHub and tag `1.0.1` on the commit the
      store builds came from.
- [ ] Any fix after the upload needs a version bump (1.0.2): Play and App Store Connect reject a
      repeated build number (10001).
