#!/usr/bin/env bash
#
# Copyright (c) 2026, Jerold Abramson
#
# This source code is licensed under the BSD 3-Clause License found in
# the LICENSE file in the root directory of this source tree.
#

# Builds the desktop app from this checkout and installs it as a native Linux
# package -- the Linux counterpart of `./gradlew :desktopApp:installDmg`.
# (install.sh, by contrast, downloads a published GitHub release.)
#
#   scripts/installLinuxFromPackage.sh                      # auto: rpm if dnf is present, else deb
#   scripts/installLinuxFromPackage.sh rpm|deb              # force a format
#   scripts/installLinuxFromPackage.sh --uninstall [rpm|deb] # remove it again
#
# A script rather than a Gradle task because installing needs sudo, and the
# Gradle daemon has no terminal for sudo to prompt on.
set -euo pipefail
cd "$(dirname "$0")/.." >/dev/null   # quiet even when CDPATH is set

PACKAGE=iperf3kmp   # jpackage lowercases packageName for both rpm and deb

uninstall=false
if [[ "${1:-}" == --uninstall ]]; then uninstall=true; shift; fi
format="${1:-}"
if [[ -z "$format" ]]; then
    if command -v dnf >/dev/null; then format=rpm
    elif command -v apt-get >/dev/null; then format=deb
    else echo "Neither dnf nor apt-get found; pass rpm or deb explicitly." >&2; exit 1
    fi
fi

case "$format" in
    rpm) task=packageRpm ;;
    deb) task=packageDeb ;;
    *) echo "Usage: $0 [--uninstall] [rpm|deb]" >&2; exit 1 ;;
esac

if $uninstall; then
    if [[ "$format" == rpm ]]; then
        if ! rpm -q "$PACKAGE" >/dev/null 2>&1; then echo "$PACKAGE is not installed."; exit 0; fi
        # jpackage's %preun only removes the menu entry after checking the
        # .desktop file's owner with `rpm -q --whatprovides`, which fails while
        # dnf5 holds the rpmdb lock ("can't create transaction lock") -- so the
        # entry would be left behind pointing at a deleted app. Remove it first.
        # (Anchored on /lib/<file> so the bundled JDK's runtime/legal/java.desktop
        # directory doesn't match.)
        rpm -ql "$PACKAGE" | grep '/lib/[^/]*\.desktop$' | while read -r desktop; do
            sudo xdg-desktop-menu uninstall "$desktop"
        done
        sudo dnf remove -y "$PACKAGE"
    else
        # dpkg -S in the deb's prerm reads the dpkg database without a lock,
        # so the menu entry is removed normally there.
        sudo apt-get remove -y "$PACKAGE"
    fi
    exit 0
fi

./gradlew ":desktopApp:$task"

# Newest artifact, so a VERSION bump or a different arch needs no edit here.
pkg=$(ls -t "desktopApp/build/compose/binaries/main/$format"/*."$format" | head -1)
echo "Installing $pkg"

# A rebuild at the same version needs reinstall: dnf install sees it as
# "already installed" and exits 0 without replacing anything.
if [[ "$format" == rpm ]]; then
    if [[ "$(rpm -q "$(rpm -qp --qf '%{NAME}' "$pkg")" 2>/dev/null)" == "$(rpm -qp --qf '%{NEVRA}' "$pkg")" ]]; then
        sudo dnf reinstall -y "./$pkg"
    else
        sudo dnf install -y "./$pkg"
    fi
else
    sudo apt-get install -y --reinstall "./$pkg"
fi
