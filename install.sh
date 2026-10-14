#!/usr/bin/env bash
# One-line installer for iperf3KMP on Linux / macOS. See README.md.
#
#   curl -fsSL https://raw.githubusercontent.com/jerryabramson/iperf3-kmp/master/install.sh | bash
#
# Downloads the right installer for this OS/arch from the latest GitHub
# release and installs it (.dmg on macOS, .deb/.rpm on Linux). The GUI and
# CLI ship in the same package - see CliArgs.kt's usage() for CLI flags once
# installed.
set -euo pipefail

cat >&2 <<'EOF'
*** NOTICE: this install script is pre-release and still being tested. ***
If you'd rather not run it yet, download the installer for your platform
directly instead: see the "Platform Installers" table in README.md, or
https://github.com/jerryabramson/iperf3-kmp/releases

EOF

# Read from /dev/tty, not stdin - when run as `curl ... | bash`, stdin is the
# piped script source itself, not the terminal, so a plain `read` here would
# just consume (and likely break on) the rest of the script.
if [ ! -r /dev/tty ]; then
  echo "No terminal available to confirm - re-run this script interactively (not piped) to confirm." >&2
  exit 1
fi
read -r -p "Continue with the install? [y/N] " reply < /dev/tty
case "$reply" in
  [yY]|[yY][eE][sS]) ;;
  *) echo "Aborted." >&2; exit 0 ;;
esac

REPO="jerryabramson/iperf3-kmp"
API_URL="https://api.github.com/repos/${REPO}/releases"

os="$(uname -s)"
arch="$(uname -m)"

case "$arch" in
  arm64|aarch64) arch_tag="arm64" ;;
  x86_64|amd64)  arch_tag="amd64" ;;
  *)
    echo "Unsupported architecture: $arch" >&2
    exit 1
    ;;
esac

# Pick the asset-name pattern for this OS/arch. The repo's only release so
# far is marked pre-release, so GitHub's own /releases/latest redirect
# (which always skips pre-releases) can't be used - the /releases API lists
# every release's assets instead, newest first, so matching against that
# full list and taking the first hit naturally prefers the newest release
# while still working if an older release is ever the only one with a given
# asset.
case "$os" in
  Darwin)
    if [ "$arch_tag" != "arm64" ]; then
      echo "macOS builds are Apple Silicon (arm64) only - no x86_64 package exists." >&2
      exit 1
    fi
    pattern='\.dmg$'
    ;;
  Linux)
    if command -v apt >/dev/null 2>&1; then
      pattern="_${arch_tag}\\.deb\$"
    elif command -v dnf >/dev/null 2>&1 || command -v rpm >/dev/null 2>&1; then
      rpm_arch="x86_64"
      [ "$arch_tag" = "arm64" ] && rpm_arch="aarch64"
      pattern="\\.${rpm_arch}\\.rpm\$"
    else
      echo "No supported package manager found (need apt, or dnf/rpm)." >&2
      exit 1
    fi
    ;;
  *)
    echo "Unsupported OS: $os. On Windows, use install.ps1 instead." >&2
    exit 1
    ;;
esac

echo "iperf3KMP installer: looking up the latest release for $os/$arch_tag..."
urls="$(curl -fsSL "$API_URL" \
  | grep -o '"browser_download_url": *"[^"]*"' \
  | sed -E 's/.*"(https:[^"]+)"/\1/')"
url="$(printf '%s\n' "$urls" | grep -E "$pattern" | head -n1 || true)"

if [ -z "$url" ]; then
  echo "Could not find a matching release asset for $os/$arch_tag." >&2
  exit 1
fi

file="$(basename "$url")"
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT
dest="$tmpdir/$file"

echo "Downloading $file..."
curl -fsSL -o "$dest" "$url"

case "$os" in
  Darwin)
    # Mounting+copying here (rather than telling the user to double-click the
    # .dmg) also sidesteps the Gatekeeper quarantine flag entirely: that flag
    # is applied by the browser on a browser download, not by curl, so a
    # script-driven install like this never triggers the "is damaged" Gatekeeper
    # message a manual browser download of an unnotarized build used to.
    mountpoint="$(mktemp -d)"
    echo "Mounting $file..."
    hdiutil attach "$dest" -mountpoint "$mountpoint" -nobrowse -quiet
    app="$(find "$mountpoint" -maxdepth 1 -name '*.app' | head -n1)"
    if [ -z "$app" ]; then
      hdiutil detach "$mountpoint" -quiet || true
      echo "No .app bundle found inside $file" >&2
      exit 1
    fi
    appname="$(basename "$app")"
    echo "Installing $appname to /Applications..."
    rm -rf "/Applications/$appname"
    if ! cp -R "$app" /Applications/ 2>/dev/null; then
      echo "Need elevated privileges to write to /Applications..."
      sudo cp -R "$app" /Applications/
    fi
    hdiutil detach "$mountpoint" -quiet
    launcher="/Applications/$appname/Contents/MacOS/${appname%.app}"
    echo "Installed. Launch it from Applications/Spotlight, or run the CLI directly:"
    echo "  \"$launcher\" --help"
    ;;
  Linux)
    echo "Installing $file (you may be prompted for your password)..."
    if command -v apt >/dev/null 2>&1; then
      sudo apt install -y "$dest"
    else
      sudo dnf install -y "$dest"
    fi
    # jpackage always installs the deb/rpm under /opt/<lowercased packageName>/
    # with a launcher binary that keeps packageName's exact casing - see
    # CLAUDE.md's packaging section.
    launcher="$(find /opt/iperf3kmp/bin -maxdepth 1 -type f 2>/dev/null | head -n1)"
    if [ -n "$launcher" ] && [ ! -e /usr/local/bin/iperf3kmp ]; then
      sudo ln -s "$launcher" /usr/local/bin/iperf3kmp 2>/dev/null \
        && echo "Symlinked as 'iperf3kmp' on your PATH." || true
    fi
    echo "Installed. Run it with:"
    if [ -n "$launcher" ]; then
      echo "  iperf3kmp --help    (or: \"$launcher\" --help)"
    else
      echo "  /opt/iperf3kmp/bin/iperf3KMP --help"
    fi
    ;;
esac

echo "Done."
