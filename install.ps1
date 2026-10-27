# One-line installer for iperf3KMP on Windows. See README.md.
#
#   irm https://raw.githubusercontent.com/jerryabramson/iperf3-kmp/master/install.ps1 | iex
#
# Downloads the newest Windows .msi from the GitHub releases and installs it.
# There is one .msi, x86_64; on Windows on Arm it runs under Prism emulation.
# The .msi is a per-user install (%LOCALAPPDATA%\iperf3KMP), so no
# administrator rights or UAC prompt are needed. It isn't code-signed, but a
# file downloaded by PowerShell (unlike one downloaded by a browser) doesn't
# get the "downloaded from the internet" mark, so SmartScreen normally doesn't
# prompt for this route. Browser download instructions are in README.md.
#
# Everything runs inside a script block: under `irm | iex`, `exit` would close
# the user's PowerShell window, so the block uses `return` instead.

& {
    $ErrorActionPreference = 'Stop'
    # Windows PowerShell 5.1 shows a progress bar that slows Invoke-WebRequest
    # down a lot, and may default to TLS 1.0, which GitHub refuses.
    $ProgressPreference = 'SilentlyContinue'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

    $repo = 'jerryabramson/iperf3-kmp'

    Write-Host '*** NOTICE: this install script is pre-release and still being tested. ***' -ForegroundColor Yellow
    Write-Host 'If you would rather not run it yet, download the .msi directly instead:'
    Write-Host "  https://github.com/$repo/releases"
    Write-Host ''
    $reply = Read-Host 'Continue with the install? [y/N]'
    if ($reply -notmatch '^(y|yes)$') { Write-Host 'Aborted.'; return }

    $arch = $env:PROCESSOR_ARCHITECTURE
    if ($env:PROCESSOR_ARCHITEW6432) { $arch = $env:PROCESSOR_ARCHITEW6432 }
    if ($arch -eq 'ARM64') {
        Write-Host 'Windows on Arm: installing the x86_64 build, which runs under Windows'' x64 emulation (Prism).'
    } elseif ($arch -ne 'AMD64') {
        Write-Host "Unsupported architecture: $arch (only x86_64 and ARM64 Windows are supported)." -ForegroundColor Red
        return
    }

    # The repo's releases API lists every release, newest first, including
    # pre-releases (which /releases/latest skips) - same approach as install.sh.
    # Pick the highest version among all .msi assets by its file name
    # (iperf3KMP-<major>.<minor>.<patch>.msi), not just the first one listed.
    Write-Host 'Looking up the newest Windows release...'
    $releases = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Headers @{ 'User-Agent' = 'iperf3kmp-install' }
    $msi = $releases |
        ForEach-Object { $_.assets } |
        Where-Object { $_.name -match '^iperf3KMP-(\d+\.\d+\.\d+)\.msi$' } |
        Sort-Object { [version]($_.name -replace '^iperf3KMP-(\d+\.\d+\.\d+)\.msi$', '$1') } -Descending |
        Select-Object -First 1
    if (-not $msi) {
        Write-Host "Could not find a Windows .msi on https://github.com/$repo/releases" -ForegroundColor Red
        return
    }

    # A 1.0.0 .msi installed per machine (Program Files). A per-user .msi
    # doesn't upgrade that one, so you'd end up with two copies.
    if (Test-Path "$env:ProgramFiles\iperf3KMP\iperf3KMP.exe") {
        Write-Host "Note: an older per-machine copy is installed in $env:ProgramFiles\iperf3KMP." -ForegroundColor Yellow
        Write-Host '      Uninstall it from Settings > Apps to avoid two copies.' -ForegroundColor Yellow
    }

    $dest = Join-Path $env:TEMP $msi.name
    Write-Host "Downloading $($msi.name)..."
    Invoke-WebRequest -Uri $msi.browser_download_url -OutFile $dest -UseBasicParsing

    Write-Host "Installing $($msi.name) for the current user..."
    $log = Join-Path $env:TEMP 'iperf3kmp-install.log'
    $proc = Start-Process -FilePath 'msiexec.exe' -ArgumentList @('/i', "`"$dest`"", '/passive', '/norestart', '/l*v', "`"$log`"") -Wait -PassThru
    Remove-Item $dest -ErrorAction SilentlyContinue

    switch ($proc.ExitCode) {
        0 { }
        3010 { Write-Host 'Installed; Windows asks for a restart to finish.' -ForegroundColor Yellow }
        1602 { Write-Host 'Install cancelled.'; return }
        default {
            Write-Host "msiexec failed with exit code $($proc.ExitCode). Log: $log" -ForegroundColor Red
            return
        }
    }
    Remove-Item $log -ErrorAction SilentlyContinue

    $launcher = Join-Path $env:LOCALAPPDATA 'iperf3KMP\iperf3KMP.exe'
    Write-Host 'Installed. Start it from the Start menu (Network Tools > iperf3KMP), or run the CLI:'
    Write-Host "  & `"$launcher`" --help"
    Write-Host 'Done.'
}
