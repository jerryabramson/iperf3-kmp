#!/usr/bin/env bash
#
# Copyright (c) 2026, Jerold Abramson
#
# This source code is licensed under the BSD 3-Clause License found in
# the LICENSE file in the root directory of this source tree.
#

# Builds the desktop app from this checkout and installs it into /Applications
# -- the macOS counterpart of scripts/installLinuxFromPackage.sh.
# (install.sh, by contrast, downloads a published GitHub release.)
#
#   scripts/installMacFromPackage.sh
#
# Just a wrapper for `./gradlew :desktopApp:installDmg`, which does the real
# work (packageDmgLocal, then hdiutil attach + ditto, no notarization). A
# Gradle task suffices here because nothing needs sudo; keep running it from
# the command line rather than an IDE run config, so a notarizing package
# task never gets re-triggered by accident.
set -euo pipefail
cd "$(dirname "$0")/.." >/dev/null   # quiet even when CDPATH is set

if [[ "$(uname -s)" != Darwin ]]; then
    echo "macOS only; on Linux use scripts/installLinuxFromPackage.sh." >&2
    exit 1
fi

exec ./gradlew :desktopApp:installDmg "$@"
