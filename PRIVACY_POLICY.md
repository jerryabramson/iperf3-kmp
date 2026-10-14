# Privacy Policy

**Effective date:** 2026-10-14

iperf3KMP ("the app") is a network performance testing tool for Android, iOS,
and desktop (macOS, Windows, Linux). This policy covers all platform builds.

## Summary

The app does not collect, transmit, or sell any personal data. It has no
backend server, no analytics, and no advertising SDKs. Everything the app
does happens locally on your device, or directly between your device and the
iperf3 server you choose to test against.

## What the app does with network access

When you run a test, the app connects directly to the iperf3 server hostname
or IP address you enter, to exchange the standard iperf3 protocol traffic
needed to measure throughput, jitter, and packet loss. That connection is
between your device and the server you specified — the app's developer is
never a party to it, and no copy of that traffic is sent anywhere else.

- **iOS** declares `NSLocalNetworkUsageDescription` because measuring
  throughput against a server on your local network requires that
  permission. It is used only for this purpose.
- No other network capability (location, contacts, microphone, camera, ads,
  tracking) is requested or used on any platform.

## What the app stores, and where

Test results (server, port, duration, measured throughput, timestamp, etc.)
are saved locally via Room/SQLite so you can view test history and export it
to CSV:

- **Desktop**: `~/.iperf3kmp/result_data-db.db`, a local file on your own
  machine.
- **Android**: the app's private app-storage database directory.
- **iOS**: the app's private container (`Documents/result_data-db.db`).

This data never leaves your device unless you deliberately export it (e.g.
CSV export, then sharing that file yourself) or back it up through your own
OS-level backup mechanism. The app itself has no feature that uploads,
syncs, or transmits this data anywhere.

## Third-party services

None. The app does not integrate any third-party analytics, crash-reporting,
advertising, or cloud-storage service.

## Children's privacy

The app does not knowingly collect any personal information from anyone,
including children, because it does not collect personal information from
anyone at all.

## Changes to this policy

If the app's data handling ever changes, this file will be updated and the
"Effective date" above revised accordingly.

## Contact

Jerold Abramson — jthea@bu.edu
