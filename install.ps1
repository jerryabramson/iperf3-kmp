# One-line installer for iperf3KMP on Windows. See README.md.
#
#   irm https://raw.githubusercontent.com/jerryabramson/iperf3-kmp/master/install.ps1 | iex
#
# Downloads the .msi from the latest GitHub release and installs it silently.
# The GUI and CLI ship in the same package - see CliArgs.kt's usage() for CLI
# flags once installed.

$ErrorActionPreference = "Stop"

Write-Host "*** NOTICE: this install script is pre-release and still being tested. ***" -ForegroundColor Yellow
Write-Host "If you'd rather not run it yet, download the installer for your platform" -ForegroundColor Yellow
Write-Host "directly instead: see the 'Platform Installers' table in README.md, or" -ForegroundColor Yellow
Write-Host "https://github.com/jerryabramson/iperf3-kmp/releases" -ForegroundColor Yellow
Write-Host ""

$confirm = Read-Host "Continue with the install? [y/N]"
if ($confirm -notmatch '^(?i:y|yes)$') {
    Write-Host "Aborted."
    exit 0
}

$repo = "jerryabramson/iperf3-kmp"
$apiUrl = "https://api.github.com/repos/$repo/releases"

Write-Host "iperf3KMP installer: looking up the latest release..."

# The repo's only release so far is marked pre-release, so GitHub's
# /releases/latest redirect (which always skips pre-releases) can't be used -
# the /releases API returns every release newest-first instead, and this
# takes the first one, same approach as install.sh.
$releases = Invoke-RestMethod -Uri $apiUrl -Headers @{ "User-Agent" = "iperf3kmp-installer" }
$assets = $releases[0].assets

$arch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture
$isArm = $arch -eq [System.Runtime.InteropServices.Architecture]::Arm64

# Only an x86_64 .msi has actually been uploaded so far (see README's
# "Windows on ARM: TBD" row) - on ARM64 this installs the x86_64 build, which
# Windows runs under Prism emulation.
$msi = $assets | Where-Object { $_.name -like "*.msi" -and $_.name -notlike "*arm64*" } | Select-Object -First 1
if (-not $msi) {
    $msi = $assets | Where-Object { $_.name -like "*.msi" } | Select-Object -First 1
}
if (-not $msi) {
    Write-Error "Could not find a .msi asset in the latest release."
    exit 1
}

if ($isArm -and $msi.name -notlike "*arm64*") {
    Write-Host "Note: no native Windows ARM64 build is published yet - installing the x86_64 build, which will run under Prism emulation."
}

$dest = Join-Path $env:TEMP $msi.name
Write-Host "Downloading $($msi.name)..."
Invoke-WebRequest -Uri $msi.browser_download_url -OutFile $dest

Write-Host "Installing $($msi.name) (you may see a UAC prompt)..."
$proc = Start-Process msiexec.exe -ArgumentList "/i `"$dest`" /qn /norestart" -Wait -PassThru
if ($proc.ExitCode -ne 0) {
    Write-Error "msiexec failed with exit code $($proc.ExitCode)."
    exit 1
}

Remove-Item $dest -ErrorAction SilentlyContinue

Write-Host "Installed. Launch iperf3KMP from the Start Menu, or for the CLI, find it under:"
Write-Host "  C:\Program Files\iperf3KMP\iperf3KMP.exe --help"
