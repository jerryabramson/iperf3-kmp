# STORE_TODOS

Tracking doc for work needed to distribute `iperf3KMP` commercially on
**three separate stores** — Apple App Store (iOS), Apple App Store (macOS),
and Google Play (Android) — plus the eventual, currently-deferred goal of a
single-purchase/subscription model via an independent channel to avoid
store commissions. Distinct from `TODO.md` (the research-track
tracking doc) and `KMPCOMPARISONS.md` (the code-size/complexity comparison
plan) — this file is commercialization-only. Nothing here is started yet;
this is a scoping list, captured 2026-08-27 alongside the goals themselves
(see the two findings the user was given the same session, folded in
below).

## Branding & naming

- [ ] Decide the final commercial product name/brand. Constraint carried
      over from `third_party/iperf`'s license (`third_party/iperf/LICENSE`,
      LBL/UC, BSD-3-Clause-style): clause (3) prohibits using the
      iperf/LBL/UC/DOE names to **endorse or promote** a derived product —
      so the commercial brand shouldn't lean on "iperf" as its own name in
      a way that implies affiliation or endorsement, independent of
      whatever Apple's/Google's own trademark-use policies also require.
- [ ] Check trademark availability for the chosen name before committing to
      it anywhere (store listing, marketing, code identifiers).
- [ ] Decide the new bundle-ID/`applicationId` scheme (e.g.
      `com.<company>.<product>`) **before** creating any store listing —
      unlike a normal rename, changing these later is a new app from each
      store's perspective (new signing identity, no continuity with a
      listing already created under the old one).

## App identity — technical rebrand (once naming is decided)

- [ ] iOS: `PRODUCT_BUNDLE_IDENTIFIER` in `iosApp/Configuration/Config.xcconfig`
      (currently `com.jabramson.iperf3-network-tester-kmp`)
- [ ] Android: `applicationId` in `androidApp/build.gradle.kts` (currently
      `com.jabramson.iperf3_network_tester_kmp`)
- [ ] Desktop: `packageName` in `desktopApp/build.gradle.kts` (currently
      `iperf3KMP`) and installer metadata/filenames that derive from it
- [ ] New app icon / branding assets if the name itself changes (current
      icon pipeline is documented in CLAUDE.md's "Application icon"
      section — same per-platform format gotchas apply to any replacement)
- [ ] Sweep `docs/`/`CLAUDE.md`/`README.md` for the old
      `com.jabramson.*` identifiers once the new ones are
      final, so internal docs don't drift from the shipped app

## Apple App Store — iOS

- [ ] Apple Developer Program enrollment under whatever legal entity will
      hold the listing (individual vs. organization — affects the
      developer name shown on the listing)
- [ ] App Store Connect listing: screenshots per required device class,
      description, keywords, support URL, marketing URL
- [ ] Privacy "nutrition label" — confirm exactly what the shipped app
      collects/transmits before writing it (today: Room/SQLite persistence
      is local-only per CLAUDE.md's Room section; no analytics/tracking
      currently wired in — verify this is still true at submission time)
- [ ] In-app purchase / subscription product setup (StoreKit) if selling
      through the App Store's own payment path
- [ ] TestFlight beta pass before public submission
- [ ] App Review guideline pass — this app opens raw client sockets to a
      user-specified host; confirm no entitlement/background-execution
      flags are needed beyond what's already set (`NSLocalNetworkUsageDescription`
      is already in `iosApp/iosApp/Info.plist`, per CLAUDE.md)

## Apple App Store — macOS

- [ ] Separate App Store Connect listing from iOS — macOS and iOS are
      distinct listings even under one developer account
- [ ] App Sandbox review — Mac App Store submissions require sandboxing;
      confirm the JNI-equivalent native-library-load pattern this app uses
      for desktop (`compileIperf3Jvm`/`compileNativeFormatJvm` bundling a
      `System.load()`ed dylib, per CLAUDE.md's native-build sections) is
      sandbox-compatible, or what entitlement it needs
- [ ] Packaging path: `desktopApp/build.gradle.kts`'s `jpackage`-based
      `packageDmg` task targets a plain `.dmg` for outside-the-store
      distribution — Mac App Store submission needs a `.pkg` built through
      Xcode/`altool`/`notarytool`, a different pipeline not yet built.
      Research whether `jpackage` can target `.pkg` directly or whether a
      separate Xcode-side wrapper is needed.
- [ ] Notarization — relevant even for any non-MAS `.dmg` distribution of
      the macOS build, separately from the Store submission itself

## Google Play — Android

- [ ] Google Play Console developer account (one-time registration fee)
- [ ] Play listing: screenshots, description, content rating
      questionnaire, Data Safety section (same "confirm what's actually
      collected" caveat as the iOS privacy label above)
- [ ] Confirm current `compileSdk`/`targetSdk` **37** (CLAUDE.md) still
      satisfies Play's minimum target-API policy at submission time — that
      policy floor moves roughly yearly
- [ ] Play Billing setup if selling through Google's own payment path
- [ ] Play App Signing key management (Google-managed upload/app signing
      keys)

## Cross-store purchase & entitlement strategy

**Researched 2026-08-27 — KMP has no first-party tooling for any of this,
confirmed against Kotlin's own docs; third-party options exist for part of
it:**

- **Release/build/submission**: no unified KMP pipeline exists or is
  planned by JetBrains — [Kotlin's own docs](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-apps.html)
  say publishing each target is identical to publishing a fully native app
  for that platform (typical Xcode project for iOS/macOS, typical Android
  process for Play). In practice this means **three independent CI
  pipelines**, hand-wired — see community guides for
  [Android](https://www.marcogomiero.com/posts/2024/kmp-ci-android/),
  [iOS](https://www.marcogomiero.com/posts/2024/kmp-ci-ios/), and
  [macOS App Store](https://www.marcogomiero.com/posts/2024/kmp-ci-macos-appstore/)
  via GitHub Actions — not one shared workflow.
- **Purchases/subscriptions**: unlike build/release, this piece **does**
  have real third-party tooling — [RevenueCat's `purchases-kmp`](https://github.com/RevenueCat/purchases-kmp)
  is an actively-maintained open-source SDK wrapping StoreKit 2 (covers
  both iOS and macOS listings) + Google Play Billing behind one Kotlin API
  for Compose Multiplatform, with Offerings/Packages/Entitlements concepts
  and server-driven paywalls ([docs](https://www.revenuecat.com/docs/getting-started/installation/kotlin-multiplatform)).
  Smaller alternatives exist too (e.g. [`In-app-purchase-kmp`](https://github.com/Aditya-gupta99/In-app-purchase-kmp)),
  less mature. **Not evaluated yet** — no decision made on adopting
  RevenueCat vs. rolling per-store `expect`/`actual` billing code by hand.
- **What none of the above solves**: the long-term goal of bypassing store
  commission via an independent channel. This isn't a tooling gap a KMP
  library can close in principle — RevenueCat/similar SDKs work *through*
  Apple's/Google's own billing systems, not around them. Routing around
  store commission is a business/legal question (Apple's anti-steering
  rules, jurisdiction-dependent post-DMA carve-outs), independent of any
  KMP tooling choice — revisit as a policy/legal question, not an
  engineering one, once the independent channel's own economics are
  reassessed.
- [ ] Decide: adopt RevenueCat's `purchases-kmp` for the near-term
      per-store purchase flow, or hand-roll `expect`/`actual` wrappers over
      StoreKit 2 / Play Billing directly.

### Commission-bypass legal research (leads only, not verified independently)

Sourced 2026-08-27 from a payments-vendor blog post
(<https://dodopayments.com/blogs/bypass-app-store-fees-legally>) — treat as
a **lead to independently verify against Apple's/Google's current actual
terms**, not as settled fact; the source has an obvious incentive to
oversell how broadly applicable this is (it's marketing for their own
checkout SDK):

- [ ] **EU Digital Markets Act (2024)** — the one genuinely promising route:
      in the EU, Apple is required to allow linking out to external
      payment with **no Apple commission** on that path. EU-only; doesn't
      help US/other-region sales. Needs real legal confirmation of current
      terms/conditions before relying on it, not just this blog's summary.
- [ ] **Epic v. Apple (2021, US)** — struck down Apple's anti-steering
      ban, so external payment links are allowed in the US too, but Apple
      still charges a commission on purchases made via that link
      (blog cites 12–27% depending on category/tenure) — **reduces**
      Apple's cut in the US, does not eliminate it. Confirm current actual
      rate against Apple's own developer terms, not the blog's number.
- [ ] **"Reader app" exemption — ruled out.** Scoped to apps that mainly
      grant access to content/services purchased elsewhere (Netflix/
      Spotify-style SaaS); a network-testing utility doesn't fit this
      category. Don't pursue this path.
- [ ] Korea/Japan/Russia reportedly have their own separate rules on this
      — not researched at all yet, the source blog didn't detail them
      either.
- [ ] If pursued: real risk noted even by the source — geo/platform-
      detecting a user specifically to show a "buy on web" link instead of
      the in-app option has led to real Apple app rejections; whatever
      external-payment UI is built needs actual App Review Guideline
      compliance, not just legal permissibility.
- [ ] Decide pricing/feature-parity policy across the three storefronts
      (same price and entitlements everywhere vs. store-specific).

## Licensing / legal

- [ ] Confirm in-app attribution/open-source notices satisfy
      `third_party/iperf`'s BSD-3-Clause-style redistribution terms
      (retain the copyright notice; don't imply LBL/UC/DOE endorsement —
      see "Branding & naming" above).
- [ ] Write a privacy policy (required by all three stores) that matches
      actual app behavior — don't reach for boilerplate; verify against
      the current no-analytics/local-only-persistence baseline first.

## Commercial app review — Apple + Google (complexities noted, not yet resolved)

Flagged 2026-08-27 as a category still **to be determined**, not researched
in depth yet — a paid/commercial listing gets more review scrutiny than a
free coursework build, on both stores:

- [ ] **Apple App Review Guideline 3.1 (In-App Purchase)** — digital
      goods/services generally must sell through Apple's own IAP, with
      narrow exemptions (reader apps, etc.) for linking to outside
      payment. Directly bears on the "eventual independent channel to
      avoid commission" goal above — needs a real read of the current
      guideline language and current exemption case law, not an assumption
      either way.
- [ ] **Apple Paid Applications Agreement** — banking/tax information in
      App Store Connect, separate from the free-app enrollment already
      needed for TestFlight/dev builds.
- [ ] **Export compliance declaration** — likely simple here since the
      client-only iperf3 build has no OpenSSL/TLS (CLAUDE.md's native
      client notes: no encryption support compiled in), but the actual
      App Store Connect questionnaire hasn't been filled out/confirmed
      yet.
- [ ] **Guideline 4.2 (Minimum Functionality) risk** — Apple has
      historically scrutinized narrow "utility"/developer-tool apps; worth
      confirming the app's feature set (Section 2 of
      `docs/project/MVP_MATURITY_ASSESSMENT.md`) reads as substantial enough, not
      assuming it will pass review as-is.
- [ ] **Auto-renewable subscription disclosure requirements** (Apple
      3.1.2) if the eventual subscription model ships through Apple's own
      IAP — specific required UI disclosures (price, duration, terms/
      privacy links, restore-purchases control) not yet designed against.
- [ ] **Google Play Payments Policy** — Play's own equivalent restrictions
      on external payment links for digital goods, not yet read against
      the specific commission-avoidance goal.
- [ ] **Review-timeline planning** — Apple's manual review vs. Google's
      largely-automated pipeline have different typical turnaround and
      rejection-iteration costs; not yet factored into any release
      timeline.
- [ ] Network-tool category positioning — how the app should be
      categorized/described for review (utility vs. developer tools vs.
      networking) hasn't been decided, and may affect review scrutiny
      given the app opens raw client sockets to a user-specified host.

## Out of scope for this file

- ICSE 2027 conference-track planning — tracked separately (not yet a
  dedicated doc; see the project's own memory of this goal if starting
  that work in a fresh session).
- `TODO.md` (research-track items) and `KMPCOMPARISONS.md`
  (code-comparison research plan) — unrelated to commercialization,
  untouched by this file.
