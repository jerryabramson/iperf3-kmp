# Build: macOS desktop (Apple Silicon)

**Status:** verified (`.dmg` built). Intel Macs are not supported.

## Prerequisites
- Common setup: [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md).
- Xcode Command Line Tools (`clang`). Native JNI libraries are compiled at build time for this machine only.

## Run / package
```shell
./gradlew :desktopApp:run
./gradlew :desktopApp:hotRun --auto          # hot reload
./gradlew :desktopApp:packageDmg             # desktopApp/build/compose/binaries/main/dmg/iperf3KMP-1.0.0.dmg
./gradlew :desktopApp:packageDmgLocal        # same, but skips notarization -- see Gotchas below
```

## Signing and notarization (your own Apple Developer ID)
Optional. With no setup, `packageDmg` builds a working but **unsigned** `.dmg`: fine on your own Mac, but Gatekeeper blocks it when someone else downloads it. To produce a `.dmg` other people can open normally, sign it with a Developer ID and notarize it with Apple.

Nothing personal is ever committed. All values go in **your own** `~/.gradle/gradle.properties` (in your home folder, never the repo's `gradle.properties`), and the Apple ID password lives in your macOS keychain.

1. **Join the Apple Developer Program** (paid). Free accounts can't create Developer ID certificates.
2. **Create a Developer ID Application certificate:** Xcode → Settings → Accounts → select your team → Manage Certificates → **+** → *Developer ID Application*. Confirm it's in your keychain:
   ```shell
   security find-identity -v -p codesigning | grep "Developer ID Application"
   ```
   Copy the quoted name, e.g. `Developer ID Application: Your Name (ABCDE12345)`. An *Apple Development* certificate won't work; Apple only notarizes Developer ID–signed apps.
3. **Store notarization credentials in the keychain.** Create an app-specific password at account.apple.com → Sign-In and Security → App-Specific Passwords, then:
   ```shell
   xcrun notarytool store-credentials iperf3kmp-notary \
     --apple-id you@example.com --team-id ABCDE12345
   ```
   It prompts for the app-specific password. `iperf3kmp-notary` is just a profile name you choose.
4. **Add to `~/.gradle/gradle.properties`:**
   ```properties
   desktopApp.macSigningIdentity=Developer ID Application: Your Name (ABCDE12345)
   desktopApp.macNotarizationProfile=iperf3kmp-notary
   desktopApp.macBundleId=com.yourname.iperf3-network-tester-kmp
   ```
   Set `macBundleId` to a reverse-DNS name you control. If you leave it out, it defaults to `com.jabramson.iperf3-network-tester-kmp`, and macOS treats your build as the same app as the official one (shared settings, privacy permissions, keychain access).
5. **Build:**
   ```shell
   ./gradlew :desktopApp:packageDmg        # sign + notarize + staple (slow; Apple round trip)
   ./gradlew :desktopApp:packageDmgLocal   # sign only, for local testing
   ```
6. **Verify:**
   ```shell
   spctl -a -t open --context context:primary-signature -vv desktopApp/build/compose/binaries/main/dmg/iperf3KMP-*.dmg
   xcrun stapler validate desktopApp/build/compose/binaries/main/dmg/iperf3KMP-*.dmg
   ```
   `spctl` should report `accepted` and `source=Notarized Developer ID`.

Each setting is independent. Without `macSigningIdentity`, nothing is signed. Without `macNotarizationProfile`, the `.dmg` is signed but `notarizeAndStapleDmg` is skipped. If Apple rejects a submission, the build fails and prints the `notarytool log` command that shows why.

## Test
```shell
./gradlew :shared:jvmTest
```

## Gotchas
- No cross-compiling: a `.dmg` built here only runs on Apple Silicon.
- Switching a checkout between OSes/architectures: delete `third_party/iperf/src/iperf_config.h` and `version.h` (or run `./gradlew cleanIperf3Configure`, as its own invocation) before building.
- The app icon must be `.icns` (already provided), not a PNG.
- **`packageDmg` always notarizes** (when `desktopApp.macNotarizationProfile` is set in your `~/.gradle/gradle.properties`) — it's `finalizedBy` a real `notarytool submit --wait` round trip to Apple, which is slow and subject to Apple's own notary-service rate limits. Running `packageDmg` repeatedly in quick succession (e.g. while iterating on packaging itself) risks hitting that limit. Use `packageDmgLocal` for local iteration instead — it produces the same signed `.dmg` without the notarization step, and only run plain `packageDmg` when you actually mean to produce a distributable build. If you do hit the rate limit, `xcrun notarytool history --keychain-profile <profile>` shows your recent submissions and their status.
