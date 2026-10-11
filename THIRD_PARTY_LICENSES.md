# Third-party licenses

`iperf3KMP`'s own code is licensed under BSD-3-Clause (see [LICENSE](LICENSE)). This file lists
the third-party components whose code or assets end up inside the apps this project builds and
ships — as opposed to ordinary Maven/Gradle dependencies, which are covered separately below.

## Bundled / vendored components

### iperf3 (`esnet/iperf`)

- **What:** the real `libiperf` client, compiled directly into this app's native library on every
  target (JNI on Android/desktop, cinterop on iOS) — see `IPERF3_NATIVE_INTEGRATION.md`. Not a
  separate shared library loaded at runtime; its source is compiled into this app's own binary.
- **Where:** `third_party/iperf`, a git submodule pinned to the `3.21` tag
  (`https://github.com/esnet/iperf`). Run `git submodule update --init --recursive` after cloning
  to pull it in.
- **License:** BSD-3-Clause-style, Copyright (c) 2014–2026 The Regents of the University of
  California, through Lawrence Berkeley National Laboratory. Full text travels with the submodule
  at `third_party/iperf/LICENSE` and is reproduced for convenience at
  [`THIRD_PARTY_LICENSES/iperf-LICENSE.txt`](THIRD_PARTY_LICENSES/iperf-LICENSE.txt).
- **Compatible** with this project's own BSD-3-Clause license — same license family, same
  attribution/no-endorsement terms.

### JetBrains Mono Nerd Font

- **What:** the app's monospace UI font (`shared/src/commonMain/kotlin/.../ui/theme/mesloFontFamily.kt`,
  historically named for a different font — see that file's header comment).
- **Where:** `shared/src/commonMain/composeResources/font/jetbrainsmononerdfont.ttf` (+ bold/italic).
  Patched from JetBrains Mono by the [Nerd Fonts](https://github.com/ryanoasis/nerd-fonts) project
  (adds Private-Use-Area icon glyphs; the patcher itself is MIT-licensed).
- **License:** SIL Open Font License, Version 1.1. Copyright 2020 The JetBrains Mono NL Project
  Authors. Full text: [`THIRD_PARTY_LICENSES/JetBrainsMonoNerdFont-OFL.txt`](THIRD_PARTY_LICENSES/JetBrainsMonoNerdFont-OFL.txt).
  "JetBrains Mono" is a trademark of JetBrains s.r.o.; the OFL's reserved-name clause restricts
  calling a *modified* version of the font by that name, not redistributing it as-is, which is all
  this project does.
- **Note:** this app previously bundled a Menlo-derived "Meslo Nerd Font" variant. Apple's Menlo is
  not freely redistributable, so it was swapped for this one before the first public release — see
  the git history on `shared/src/commonMain/composeResources/font/` if you're curious.

### Cygwin (`cygwin1.dll` + runtime), Windows desktop build only

- **What:** the Windows `.msi` build spawns a Cygwin-built `iperf3.exe` as a subprocess (see
  `docs/platform-builds/windows-x86_64.md` / `windows-arm64.md`) and bundles `cygwin1.dll` alongside
  it, since this platform doesn't use the in-process JNI path the other targets use.
- **License:** Cygwin's runtime is GPL-licensed (the API library specifically LGPLv3+), with an
  exception that explicitly covers *static* linking against `libcygwin.a`/`crt0.o`/`gcrt0.o`.
  **That exception's text does not explicitly address redistributing the `cygwin1.dll` *runtime*
  itself** inside a third-party installer — see [cygwin.com/licensing.html](https://cygwin.com/licensing.html).
  Since this project is itself open source, the standard FOSS exception language likely applies,
  but this hasn't been confirmed against the full `COPYING`/`COPYING.LIB` texts or a qualified
  opinion. **Treat this as unresolved — verify before distributing a Windows build publicly,**
  rather than assuming it's covered.

## Gradle/Maven dependencies

Everything else (Kotlin, Compose Multiplatform, AndroidX/Jetpack libraries including Room, etc.) is
resolved as an ordinary Maven dependency, not vendored into this repo's source. Those projects are
almost entirely Apache License 2.0. Per standard practice, their license text travels with their
own published artifacts rather than being duplicated here; see each dependency's own repository if
you need its exact terms.
