<#
.SYNOPSIS
Finds and runs the installed iperf3KMP desktop launcher (from the .msi
package), forwarding all arguments and its real exit code -- so with the
app installed via the MSI, this just works:

    .\iperf3kmp.ps1 my-iperf3-server.example.com -t 10 -P 4
    .\iperf3kmp.ps1 --help

Unlike https://github.com/jerryabramson/BandWidthTester's own PowerShell
wrapper, this one does NOT need to set $Env:TERM/$Env:LANG before invoking
the launcher -- that project's ANSI-vs-dumb-terminal detection only checked
TERM, which Windows consoles never set, so it needed that workaround to get
color output at all. iperf3KMP's AnsiTerminal fixes that at the source
instead (see its doc comment): it treats Windows as always ANSI-capable,
matching what Windows Terminal/PowerShell/cmd have actually supported
natively since Windows 10.

Override: set $env:IPERF3KMP_BIN to the launcher's .exe path directly to
skip the search below -- useful for a non-default install location.
#>

$ErrorActionPreference = 'Stop'

if ($env:IPERF3KMP_BIN) {
    & $env:IPERF3KMP_BIN @args
    exit $LASTEXITCODE
}

$candidates = @(
    "$env:ProgramFiles\iperf3KMP\iperf3KMP.exe",
    "${env:ProgramFiles(x86)}\iperf3KMP\iperf3KMP.exe",
    "$env:LOCALAPPDATA\iperf3KMP\iperf3KMP.exe"
)

foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        & $candidate @args
        exit $LASTEXITCODE
    }
}

# Not at a known install location -- maybe it's already on PATH under its own name.
$onPath = Get-Command iperf3KMP.exe -ErrorAction SilentlyContinue
if ($onPath) {
    & $onPath.Source @args
    exit $LASTEXITCODE
}

Write-Error "iperf3kmp: could not find the installed iperf3KMP launcher. Checked:`n$($candidates -join "`n")`nIf it's installed somewhere else, set `$env:IPERF3KMP_BIN to the .exe path and re-run."
exit 1
