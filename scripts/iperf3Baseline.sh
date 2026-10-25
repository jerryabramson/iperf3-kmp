#!/usr/bin/env bash
#
# Copyright (c) 2026, Jerold Abramson
#
# This source code is licensed under the BSD 3-Clause License found in
# the LICENSE file in the root directory of this source tree.
#

# Native iperf3 throughput baseline for the Windows-on-ARM comparison: run
# inside WSL2 (an aarch64 distro) so iperf3 itself is native, then compare
# against the x64 and ARM64 MSIs, whose bundled iperf3.exe is x86_64 under
# Prism either way. Same test matrix as the app runs should use:
# -P 1 / -P 4, forward / reverse (-R), RUNS runs each.
#
#   scripts/iperf3Baseline.sh <host> [port] [seconds] [runs]
#   scripts/iperf3Baseline.sh jabramson.com 5201 30 5
#
# Prints a summary table and writes every run to build/iperf3-baseline-*.csv.
# Bitrate is the receiver-side sum (what iperf3's summary "receiver" line
# reports); CPU is iperf3's own local/remote total CPU %.
# WSL2 should use networkingMode=Mirrored (~/.wslconfig on Windows) so this
# goes out the same NIC as the Windows app, not through WSL's NAT.
set -euo pipefail
cd "$(dirname "$0")/.." >/dev/null   # quiet even when CDPATH is set

host="${1:?usage: $0 <host> [port] [seconds] [runs]}"
port="${2:-5201}"
seconds="${3:-30}"
runs="${4:-5}"

command -v iperf3 >/dev/null || { echo "iperf3 not found (apt install iperf3)" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 not found" >&2; exit 1; }

# One iperf3 -J result (stdin) -> one CSV row.
parse_run='
import json, sys
case, run = sys.argv[1], sys.argv[2]
end = json.load(sys.stdin)["end"]
recv = end["sum_received"]["bits_per_second"] / 1e6
cpu = end.get("cpu_utilization_percent", {})
local_cpu = cpu.get("host_total", 0)
remote_cpu = cpu.get("remote_total", 0)
retr = end.get("sum_sent", {}).get("retransmits", "")
print("%s,%s,%.2f,%.1f,%.1f,%s" % (case, run, recv, local_cpu, remote_cpu, retr))
'

mkdir -p build
csv="build/iperf3-baseline-$(date +%Y%m%d-%H%M%S).csv"
echo "case,run,mbps_received,cpu_local_pct,cpu_remote_pct,retransmits" > "$csv"

echo "iperf3 $(iperf3 --version | head -1 | awk '{print $2}') on $(uname -m), host=$host:$port, ${seconds}s x $runs runs"

for streams in 1 4; do
    for dir in fwd rev; do
        case_name="P${streams}-${dir}"
        flags=(-c "$host" -p "$port" -t "$seconds" -P "$streams" -J)
        [[ "$dir" == rev ]] && flags+=(-R)
        for run in $(seq 1 "$runs"); do
            if ! json="$(iperf3 "${flags[@]}" 2>&1)"; then
                # iperf3 -J still prints JSON with an "error" field on failure
                echo "  $case_name run $run failed: $(python3 -c 'import json,sys; print(json.load(sys.stdin).get("error","?"))' <<<"$json" 2>/dev/null || echo "$json" | head -1)" >&2
                sleep 2
                continue
            fi
            python3 -c "$parse_run" "$case_name" "$run" <<<"$json" >> "$csv"
            echo "  $case_name run $run: $(tail -1 "$csv" | cut -d, -f3) Mbit/s"
            sleep 2   # let the server settle between runs
        done
    done
done

echo
python3 - "$csv" <<'PY'
import csv, statistics, sys
rows = list(csv.DictReader(open(sys.argv[1])))
print(f'{"case":<9} {"n":>2} {"mean":>9} {"stdev":>8} {"min":>9} {"max":>9} {"cpu%":>6}  (Mbit/s)')
for case in dict.fromkeys(r["case"] for r in rows):
    v = [float(r["mbps_received"]) for r in rows if r["case"] == case]
    c = [float(r["cpu_local_pct"]) for r in rows if r["case"] == case]
    sd = statistics.stdev(v) if len(v) > 1 else 0.0
    print(f'{case:<9} {len(v):>2} {statistics.mean(v):>9.1f} {sd:>8.1f} {min(v):>9.1f} {max(v):>9.1f} {statistics.mean(c):>6.1f}')
PY
echo "raw runs: $csv"
