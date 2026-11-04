# iperf3KMP 1.0.2: sanity tests (desktop and mobile)

Written 2026-11-04 for the single 1.0.2 release across all platforms (`themes-1.0.1.1`
at `21b396b`). Packages: `~/iperf3kmp-builds/20261104-150428/` (see that folder's README).
Use the same network and server (`gb10`, port 5201) everywhere, so throughput numbers
can be compared.

## Part 1: desktop (the same tests on every package)

| Package | Test machine | File |
|---|---|---|
| macOS arm64 | Mac | `iperf3KMP-1.0.2.dmg` |
| Linux aarch64 `.deb` | Debian 13 VM | `iperf3kmp_1.0.2_arm64.deb` |
| Linux aarch64 `.rpm` | Fedora 44 VM | `iperf3kmp-1.0.2-1.aarch64.rpm` |
| Linux x86_64 `.deb` | Debian/Ubuntu x86_64 | `iperf3kmp_1.0.2_amd64.deb` |
| Linux x86_64 `.rpm` | Fedora/RHEL x86_64 | `iperf3kmp-1.0.2-1.x86_64.rpm` |
| Windows x64 | proart1 / clean x64 PC | `msi-x64/iperf3KMP-1.0.2.msi` |
| Windows ARM64 | yoga1 / Windows ARM64 | `msi-arm64/iperf3KMP-1.0.2.msi` |

CLI path after install: macOS `/Applications/iperf3KMP.app/Contents/MacOS/iperf3KMP`;
Linux `/opt/iperf3kmp/bin/iperf3KMP`; Windows `iperf3KMP.exe` in the per-user install
folder under `%LOCALAPPDATA%`. Starting it with any argument runs the CLI; with none it
opens the GUI.

### A. Install and uninstall

| # | Test | Pass when |
|---|---|---|
| A1 | Install the way a user would: `.dmg` → Applications; `sudo apt install ./….deb`; `sudo dnf install ./….rpm`; double-click the `.msi` | Installs without errors, over 1.0.1 where present |
| A2 | Launcher | App icon present: Dock / Launchpad, Linux app menu, Windows Start menu. Starts with the iperf3KMP icon, not the Java icon |
| A3 | Uninstall: drag to Trash; `sudo apt remove iperf3kmp`; `sudo dnf remove iperf3kmp`; Windows Apps & features | Removes cleanly, menu entry gone (on Debian, re-check the earlier `prerm` regression) |

### B. GUI

| # | Test | Pass when |
|---|---|---|
| B1 | Version | Shows **1.0.2** |
| B2 | Download test to `gb10` | Live progress, then a summary with Average, Max, Min, Median and Samples |
| B3 | Upload test, 8 streams | Upload styling; the stream count matches |
| B4 | Stop mid-test, then run again | Stops cleanly; the next run works |
| B5 | Unreachable host; bad port | Clear error each time; the app stays usable |
| B6 | History: save, open, expand, delete, Export CSV | Numbers match the main screen; the CSV is written. **Windows ARM64: History is disabled by design** (no SQLite native library for Windows ARM64), so check it fails gracefully |
| B7 | Light and dark system theme | Main, running and **History** screens follow the theme; History cards dark and readable in dark mode; fonts consistent |
| B8 | Resize the window small and large | The layout adapts (compact top bar when small); nothing clipped. On Windows, no console window appears when started from the Start menu |

### C. CLI

| # | Command | Pass when |
|---|---|---|
| C1 | `iperf3KMP -V` / `--version` | Prints 1.0.2 |
| C2 | `iperf3KMP -h` | Help text; exit code 0 |
| C3 | `iperf3KMP -c gb10 -t 10 -P 8 -O 2 -R` (download) | Per-interval lines, then a summary; exit code 0 |
| C4 | `iperf3KMP -c gb10 -t 10` (upload is the default) | Upload summary; exit code 0 |
| C5 | `iperf3KMP -c gb10 -t 5 -l 3` (loop 3 times) | Three runs, each with a summary |
| C6 | `iperf3KMP -c no-such-host`; `iperf3KMP -c gb10 -p 1` | Clear error; **non-zero** exit code |
| C7 | Ctrl-C during C3 | Stops promptly; the terminal is left in a normal state |
| C8 | Output redirected: `iperf3KMP -c gb10 -t 5 > out.txt` | The file contains clean text, without escape codes |

Exit code: `echo $?` (macOS/Linux) or `$LASTEXITCODE` (PowerShell).

## Part 2: mobile (real devices)

Devices: **iPhone 17 Pro**, **iPad M4**, **moto g stylus 2025** (Android, arm64).
Install the release candidate the way testers will get it: TestFlight on iOS, Play
internal testing on Android. On iOS, run `./gradlew syncVersions` before archiving.

### Core tests: every device

| # | Test | Pass when |
|---|---|---|
| M1 | Install and version | Installs over 1.0.1 and starts; shows version **1.0.2** |
| M2 | Download test to `gb10` (port 5201, defaults) | Live progress, then a summary with Average, Max, Min, Median and Samples |
| M3 | Upload test, 8 streams | Upload styling; the stream count matches |
| M4 | Stop mid-test | Stops cleanly; a new run works right after |
| M5 | Unreachable host; bad port | Clear error each time; the app stays usable |
| M6 | History: save, open, expand, delete, Export/Share CSV | Numbers match the main screen; delete works; the CSV is produced |
| M7 | Light and dark mode: main, running and **History** screens | History cards dark and readable in dark mode, unchanged in light mode; fonts consistent |

### iPhone 17 Pro

| Test | Pass when |
|---|---|
| Local Network permission (fresh install, first LAN test) | Asks for access; allowing it lets the test run; denying it gives an understandable error. Reset under Settings → Privacy → Local Network |
| Larger Dynamic Type (Settings → Display → Text Size) | Result rows don't clip with the new font sizes |
| Cellular only (Wi-Fi off), server `jabramson.com` | The test completes over cellular |
| Background mid-test, then return | Keeps running or stops cleanly; behaves the same each time; no crash |

### iPad M4

| Test | Pass when |
|---|---|
| Rotate portrait ↔ landscape, during a test and on History | The layout reflows; nothing overlaps; top-bar buttons stay reachable |
| Split View / Stage Manager, narrow window | Compact layout (smaller top bar and buttons); input fields stay usable |
| Hardware keyboard (if available) | Typing into the server and number fields works; Tab and Return behave sensibly |

### moto g stylus 2025 (Android)

| Test | Pass when |
|---|---|
| Edge-to-edge display (`targetSdk 37`), both themes | Top and bottom bars aren't under the status bar, the navigation bar or the camera cutout |
| System back gesture from History | Returns to the main screen; the last result is kept |
| Export CSV via the share sheet (e.g. Gmail, Drive) | The file actually attaches in the receiving app (FileProvider) |
| Rotate, or change the theme, during a running test | The test and its results survive |
| Stylus handwriting into the server field | The text is entered correctly |

Use the moto (or another Play-certified phone) for the Play internal-testing install.
The MuMu emulator isn't certified and can't install from Play.

## Results

| Package / device | Tester, date | Install/uninstall | GUI / core | CLI / device-specific | Notes |
|---|---|---|---|---|---|
| macOS `.dmg` | | | | | |
| `.deb` arm64 | | | | | |
| `.rpm` aarch64 | | | | | |
| `.deb` amd64 | | | | | |
| `.rpm` x86_64 | | | | | |
| `.msi` x64 | | | | | |
| `.msi` ARM64 | | | | | |
| iPhone 17 Pro | | — | | | |
| iPad M4 | | — | | | |
| moto g stylus 2025 | | — | | | |
