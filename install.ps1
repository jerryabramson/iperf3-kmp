# One-line installer for iperf3KMP on Windows. See README.md.
#
#   irm https://raw.githubusercontent.com/jerryabramson/iperf3-kmp/master/install.ps1 | iex
#
# Windows .msi builds are not published as GitHub release assets (unlike the
# macOS/Linux installers install.sh fetches) - the Windows distribution path
# is the Microsoft Store (listing pending certification) or building it
# yourself from source. This script can't silently install either of those,
# so it just points you at the right doc instead.

Write-Host "iperf3KMP for Windows isn't distributed as a GitHub release asset." -ForegroundColor Yellow
Write-Host ""
Write-Host "Two ways to get it:" -ForegroundColor Yellow
Write-Host "  1. Microsoft Store listing (pending certification - check back soon)." -ForegroundColor Yellow
Write-Host "  2. Build the .msi yourself:" -ForegroundColor Yellow
Write-Host "       x86_64 - docs/platform-builds/windows-x86_64.md" -ForegroundColor Yellow
Write-Host "       ARM64  - docs/platform-builds/windows-arm64.md" -ForegroundColor Yellow
Write-Host "     https://github.com/jerryabramson/iperf3-kmp/blob/master/docs/platform-builds/" -ForegroundColor Yellow
