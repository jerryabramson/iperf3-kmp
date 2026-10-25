# Windows on ARM: x64 vs ARM64 MSI throughput comparison

**Status:** procedure written 2026-10-24. At 2.5 GbE: WSL baseline (A) done, ARM64 MSI (C)
partly done (one run each of P1-fwd, P1-rev, P4-fwd), x64 MSI (B) not done. See §6. **Next
(planned 2026-10-25): repeat on a 10 GbE Thunderbolt adapter, see §5a.**

## 1. Question

On a Windows on ARM laptop, which build of iperf3KMP should users install?

| Build | JVM / UI | `iperf3.exe` | Save / History |
|---|---|---|---|
| **x64 MSI** | x86_64, emulated (Prism) | x86_64, emulated (Prism) | works |
| **ARM64 MSI** | native aarch64 | x86_64, emulated (Prism) | disabled (no SQLite `windows_arm64` binary, see [windows-arm64.md](../platform-builds/windows-arm64.md)) |

Both builds run the same x86_64 `iperf3.exe` under emulation, so the network test itself should
perform the same. Comparing the two MSIs therefore measures the JVM/UI, not iperf3. To tell
whether Prism emulation of `iperf3.exe` costs any throughput at all, a third, fully native
setup is needed as a baseline: an aarch64 `iperf3` in WSL2 on the same laptop.

**Hypothesis:** all three reach the same bitrate within run-to-run noise. If so, the
ARM64 MSI gains nothing measurable and loses Save/History, so the x64 MSI stays the
recommended Windows on ARM install. That conclusion would also let
[WINDOWS_ARM64_IPERF3_PLAN.md](WINDOWS_ARM64_IPERF3_PLAN.md) (native `iperf3.exe`) and a custom
ARM64 SQLite driver be deprioritized on evidence.

## 2. Test setup

Keep everything identical across the three setups, and record it in §6:

- **Client:** the Windows on ARM laptop (2026-10-24: NVIDIA RTX Spark N1X, Windows 11 build 28000).
- **Server:** `gb10.jabramson.com:5201`, a server on the same LAN. A LAN server keeps the spread
  small, so differences of a few percent are real.
- **Same link** for every run (same cable/port, or same Wi-Fi network/band), and **AC power
  plugged in**; ARM laptops can throttle on battery.
- Close bandwidth-heavy apps (cloud sync, updates, streaming). Don't run other network tests
  at the same time.
- Run the three setups back to back, in one session if possible.

**Test matrix:** 4 cases × 5 runs × 30 s, with ~2 s between runs.

| Case | Streams (`-P`) | Direction |
|---|---|---|
| P1-fwd | 1 | client → server (upload) |
| P1-rev | 1 | server → client (download, `-R`) |
| P4-fwd | 4 | upload |
| P4-rev | 4 | download (`-R`) |

**Record per run:** the **receiver** bitrate from iperf3's final summary (Mbit/s) and,
for the MSI runs, `iperf3.exe`'s CPU (§4.3).

## 3. Setup A: native baseline (WSL2, aarch64 iperf3)

### 3.1 One-time prerequisites

1. An **aarch64** WSL2 distro. Check with `wsl -d Debian -- uname -m`, which should print `aarch64`.
2. `iperf3` and `python3` inside it: `wsl -d Debian -- sudo apt install -y iperf3 python3`.
3. **Mirrored networking**, so WSL uses the laptop's real NICs instead of NAT, the same path
   the Windows app uses. In `%USERPROFILE%\.wslconfig`:
   ```ini
   [wsl2]
   networkingMode=Mirrored
   ```
   Then `wsl --shutdown`. Check with `wsl -d Debian -- ip -4 addr`: the laptop's LAN address
   should appear.

### 3.2 Run

From the repo root, in a Windows terminal:
```
wsl -d Debian -- scripts/iperf3Baseline.sh gb10.jabramson.com 5201 30 5
```
`scripts/iperf3Baseline.sh` runs the full matrix (about 11 minutes), prints a summary table
(mean, standard deviation, min, max, local CPU %), and writes every run to
`build/iperf3-baseline-<timestamp>.csv`. Copy the summary table into §6.

Note: Debian 13's `iperf3` is 3.18; the app bundles 3.21. The data-path code is effectively
unchanged between them; record the versions anyway.

## 4. Setups B and C: x64 MSI and ARM64 MSI

### 4.1 Get the two MSIs

- **ARM64 MSI:** build on the ARM laptop, `gradlew.bat :desktopApp:packageMsi` →
  `desktopApp\build\compose\binaries\main\msi\iperf3KMP-1.0.1.msi`.
- **x64 MSI:** build it on an x86_64 Windows machine with the same command, or download
  the x64 asset from the GitHub release. **Don't** build it on the ARM laptop: an x64 JDK
  there would produce x64 output, but the whole build would run emulated, and that's not
  what users get.
- Keep the two files under distinct names (e.g. `iperf3KMP-1.0.1-x64.msi`,
  `iperf3KMP-1.0.1-arm64.msi`).

Both MSIs share one upgrade code (`upgradeUuid` in `desktopApp/build.gradle.kts`), so
**installing one replaces the other**. Test them one at a time.

### 4.2 Install and confirm which build is running

1. Install the MSI under test. If the other one is installed, the new install replaces it; to
   be sure, uninstall it first (Settings → Apps).
2. Launch iperf3KMP from the Start menu.
3. Confirm the architecture: Task Manager → **Details** → right-click a column header →
   Select columns → **Architecture**. Expected:

   | Process | x64 MSI | ARM64 MSI |
   |---|---|---|
   | `iperf3KMP.exe` | x64 | ARM64 |
   | `iperf3.exe` (only while a test runs) | x64 | x64 |

   A second check: the ARM64 MSI never shows the History button (Room is disabled there),
   while the x64 MSI shows it after the first saved run.

### 4.3 Run the matrix in the app

For each case in §2, set the app's inputs to match, then run it **5 times**:

- Host `gb10.jabramson.com`, port `5201`, duration `30`.
- Parallel streams `1` or `4`.
- Reverse on for the `-rev` cases, off for `-fwd`.

After each run, copy the **receiver** line's bitrate from the iperf3 output panel (the final
summary's `receiver` row). On the x64 MSI, results are also saved and can be exported from
History → CSV export as a backup record.

**CPU of `iperf3.exe`** (optional but useful for the P1 cases): while a run is in progress,
sample it from a second PowerShell window:
```powershell
Get-Counter '\Process(iperf3)\% Processor Time' -SampleInterval 2 -MaxSamples 10 |
    ForEach-Object { $_.CounterSamples.CookedValue } | Measure-Object -Average -Maximum
```
The value is per core: 100 means one full core. Values near 100 on a P1 case mean `iperf3.exe`
is CPU-bound under emulation, which is exactly the cost this test is looking for.

## 5. Optional: CPU-bound stress test

At about 2.3 Gbit/s (2.5 GbE) the LAN link is probably the limit, which can hide an emulation
cost. To remove the network from the picture, test against a server on the laptop itself:

1. In WSL: `iperf3 -s -p 5299` (leave it running; Ctrl+C to stop).
2. Native: `wsl -d Debian -- scripts/iperf3Baseline.sh 127.0.0.1 5299 30 5`.
3. Emulated: in either MSI, host `127.0.0.1`, port `5299`, same cases. With mirrored
   networking, Windows `localhost` reaches the WSL server.

A 2 s × 2 run sanity check of this loopback path on 2026-10-24 reached about 16 Gbit/s on one
stream and about 22–24 Gbit/s on four, natively. The gap between those and the emulated
`iperf3.exe` is the upper bound of Prism's cost.

## 5a. Planned: 10 GbE run (next session)

**Why:** at 2.5 GbE the link is nearly the limit, so emulation shows only as about 4% on upload
(C ≈ 2190–2200 vs A ≈ 2276–2308 Mbit/s). At 10 GbE the network is no longer the limit, so any
CPU cost of the emulated x86_64 `iperf3.exe` shows directly. This is the run that decides
whether [WINDOWS_ARM64_IPERF3_PLAN.md](WINDOWS_ARM64_IPERF3_PLAN.md) (native `iperf3.exe`) is
worth doing.

**Setup checklist:**
1. **10 GbE end to end:** a 10 GbE Thunderbolt adapter on the laptop, a 10 GbE port on the server
   (`gb10`), and a 10 GbE switch or a direct cable. Check the link:
   `Get-NetAdapter | Select-Object Name, InterfaceDescription, Status, LinkSpeed` should show
   `10 Gbps`. If the server's 10 GbE interface has its own IP or hostname, use that.
2. **Force traffic onto the 10 GbE adapter:** unplug the dock's 2.5 GbE cable for the test.
   The app's CLI has no bind-to-interface option, so routing decides. During a run, Task
   Manager → Performance should show traffic on the 10 GbE adapter.
3. **MTU 1500 everywhere.** No jumbo frames, so the results stay comparable with the 2.5 GbE ones.
4. **AC power**, same as before.
5. **WSL check:** `wsl -d Debian -- ip -4 addr` should show the 10 GbE adapter's address
   (mirrored networking).

**Runs:**
- **A:** `wsl -d Debian -- scripts/iperf3Baseline.sh <gb10 10G address> 5201 30 5`. Only the
  `-fwd` cases count, since WSL limits download (§6).
- **C (ARM64 MSI, currently installed)** and then **B (x64 MSI)**: 5 runs of each of
  `iperf3KMP <host> -P 1 -t 30 -O 0`, the same with `-R`, `-P 4 …`, and `-P 4 … -R`.
- During the P1 and P4 upload runs, sample `iperf3.exe`'s CPU (the `Get-Counter` command in §4.3).
- Run one case with `-v` to get iperf3's own `sender`/`receiver` summary lines, for a direct
  comparison with A's receiver totals.

**What would answer the question:** natively, a single stream should get close to about 9.4 Gbit/s if
the laptop and server keep up. If the emulated `iperf3.exe` (B and C) stops well below A, with
its CPU near 100 (one full core), emulation is the bottleneck at 10 GbE. B ≈ C is still expected,
since both run the same exe.

Record the results in a separate 10 GbE table in §6.

## 6. Results

Fill in mean ± standard deviation (Mbit/s) over 5 runs; add CPU % where measured.

**Environment:** date 2026-10-24 (A), link (Ethernet 2.5 GbE / Wi-Fi ____), power ____,
WSL `iperf3` 3.18 (Debian 13, aarch64, mirrored networking), app version 1.0.1.

| Case | A: WSL native | B: x64 MSI | C: ARM64 MSI |
|---|---|---|---|
| P1-fwd | 2275.7 ± 8.2 (cpu 5.1%, retr 0–4) | | 2190 (n=1; min 2180, max 2230) |
| P1-rev | 1086.9 ± 20.5 (cpu 8.5%, retr 57–167) | | 2210 (n=1; min 1870, max 2310, median 2260) |
| P4-fwd | 2307.7 ± 2.9 (cpu 5.4%, retr 8–15) | | 2200 (n=1; min 2180, max 2260) |
| P4-rev | 1355.7 ± 18.4 (cpu 13.9%, retr 671–975) | | |

C values (2026-10-24, 20:02–20:04) come from the CLI's `Avg` (mean of the per-second
interval readings, `-O 0`), not iperf3's final receiver total that A records. In these runs the
lowest interval is close to the mean, so the TCP ramp-up second barely affects the average.

Raw runs for A: `build/iperf3-baseline-20261024-194246.csv` (not committed, since `build/` is ignored).

**Observations on A:**
- **Upload is at line rate.** About 2.28–2.31 Gbit/s is the practical TCP limit of 2.5 GbE, with
  almost no retransmits and about 5% CPU. Nothing on the laptop limits it.
- **Download is about half of upload, with heavy retransmits.** With `-R` the server is the
  sender, and the retransmit count is the server's. Hundreds of retransmits per run mean packets
  are being dropped on the way *to* the laptop. CPU is still low (8–14%), so the laptop isn't
  CPU-bound. Likely places: WSL2's mirrored-networking receive path (Hyper-V virtual switch),
  the laptop NIC/driver's receive buffers, or the server's send side.
- **Confirmed 2026-10-24: the download limit is WSL, not the laptop.** A Windows-side
  download run with the installed app's CLI (`iperf3KMP gb10 -R`, which defaults to
  `--parallel 8 --time 10 --omit 2`, so it isn't yet the matrix) reached **2.37 Gbit/s** (stdev
  3 Mbit/s), which is line rate. That run used the bundled x86_64 `iperf3.exe` under Prism. The
  NIC, an Intel I225-LMvP (`PCI\VEN_8086&DEV_5502&REV_03`) on a Thunderbolt 4 dock with Intel
  driver 3.1.5.0 (2024-11-07), reported 0 received-discarded and 0 error packets over about
  52M received. So neither the adapter nor the server drops packets. The retransmits come from
  WSL2's mirrored-networking receive path. Emulated `iperf3.exe` also isn't a bottleneck for
  download at 2.5 GbE.
- **So A is a valid native baseline for the `-fwd` cases only.** For `-rev`, A may be
  *understating* what the laptop can do. If B and C (plain Windows networking, no WSL) beat A on
  `-rev`, WSL's receive path is the limit, not emulation. In that case, compare B with C
  directly for `-rev`, and treat A's `-rev` numbers as a WSL artifact.

Optional loopback (§5):

| Case | A: WSL native | B: x64 MSI | C: ARM64 MSI |
|---|---|---|---|
| P1-fwd | | | |
| P1-rev | | | |
| P4-fwd | | | |
| P4-rev | | | |

## 7. How to read the results

- **A ≈ B ≈ C** (within about 2× the standard deviation): emulation has no measurable cost at this
  link speed. Keep recommending the x64 MSI on Windows on ARM; native `iperf3.exe` and ARM64
  SQLite stay low priority.
- **A > B ≈ C:** Prism costs throughput for `iperf3.exe`. This is the evidence for doing
  [WINDOWS_ARM64_IPERF3_PLAN.md](WINDOWS_ARM64_IPERF3_PLAN.md). The two MSIs are still
  equivalent for the test itself, so x64 stays recommended until a native `iperf3.exe`
  exists.
- **B ≠ C:** unexpected, since both use the same `iperf3.exe`. Look for a difference in
  conditions (thermal state, background load, run order) before drawing conclusions, and
  re-run the slower one.

When the results are in, update [windows-arm64.md](../platform-builds/windows-arm64.md)'s
status line ("Whether emulation affects measured throughput is untested") and the
corresponding [TEST_MATRIX.md](../platform-builds/TEST_MATRIX.md) cells.
