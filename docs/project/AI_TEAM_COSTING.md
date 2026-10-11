# Costing a team build of iperf3KMP: people, hardware, and AI

**Status:** estimate, written 2026-09-24. **Read this first:** the AI figures marked *recorded* come from real `/cost` output in
[../research/COSTS.md](../research/COSTS.md). Everything marked *estimate* (salaries, hardware prices, team size, timelines) is the author's rough
general knowledge and judgment, not measured in this project. Check the estimates against your own numbers before relying on them.

## 1. What one person plus AI actually produced (recorded)

- About 10.5 weeks (2026-07-10 to 2026-09-23), 145 commits by the time of writing, one developer directing Claude Code.
- Result: Android, iOS, macOS, Linux and Windows builds with native iperf3, persistence and packaging; a minimal text-only UI; a
  verification matrix that is still mostly untested ([../platform-builds/TEST_MATRIX.md](../platform-builds/TEST_MATRIX.md)).
- Thesis supported by this history: the coding was the easy part; environments, packaging and verification dominated
  ([../desktop/DESKTOP_SUPPORT_COMPLEXITY_NOTE.md](../desktop/DESKTOP_SUPPORT_COMPLEXITY_NOTE.md)).

## 2. Team (estimate)

**Same scope as today, from scratch: about 6 to 8 people.**

| Role | Why |
|---|---|
| Tech lead / KMP architect | Architecture, Compose Multiplatform, expect/actual |
| Native/systems engineer (C, JNI, cinterop) | iperf3 bridge, threading, FFI pitfalls |
| Build and release engineer | Per-OS toolchains, packaging, CI, signing (the hardest area in this project) |
| Windows engineer | Cygwin/MSVC/WiX, process and pipe semantics, Windows on Arm |
| Android and iOS engineers (1 to 2) | Platform behavior, store rules |
| QA/test engineer with a device lab | Owns the test matrix on real machines |

**Commercial grade: about 9 to 12 people.** Add a UI/UX designer and platform-idiomatic UI specialists (charts, native desktop menus), a product
manager, part-time legal and store compliance (see `STORE_TODOS.md`), security and signing ownership, and support. Timeline guess: 6 to 9
months. This is a weak estimate; the current app is deliberately minimal in UI, and a richer UI would also lower the code-sharing
percentage ([../research/Code Sharing Analysis.docx](<../research/Code Sharing Analysis.docx>)).

People cost is the dominant line. At a loaded cost of roughly $150k to $250k per engineer-year (US, estimate), 7 people for 9 months
is on the order of $0.8M to $1.3M; 10 people for 9 months, $1.1M to $1.9M.

## 3. Hardware lab (estimate: about $12k to $25k)

| Item | Purpose |
|---|---|
| Apple Silicon Mac (plus iPhone, iPad) | Android, iOS, macOS, container builds |
| Android device(s) | Real-device testing |
| Windows x64 PC and a Windows-on-Arm device (NVIDIA RTX WoA planned, not yet released) | `.msi` x64 and ARM64; real Prism behavior and throughput |
| arm64 Linux (GB10-class) and two x86_64 Linux machines (Fedora, Debian/Ubuntu) | `.rpm`/`.deb` on real CPUs |
| Network gear and a test server | Real throughput; VM NICs are not representative |
| Recurring | Apple Developer $99/yr, Windows code-signing certificate, CI (macOS runners are the costly ones) |

## 4. AI costs

Two bases must be kept separate:

- **Metered (API list price).** *Recorded:* sessions of $5.41, $7.54, $26.50 and $109.97 (the last a 25-hour-wall, 3 h 14 m of API time,
  100% above 150k context session; 99% of input tokens came from cache, roughly 456 cache-read tokens per output token).
- **Subscription.** The same work in this project cost $0 beyond the plan fee (usage credits unspent; see COSTS.md, "How to read the dollar
  figures"). Plan price and the token-to-percentage mapping are not shown by the usage page, so no ratio is claimed.

*Estimate, metered basis:* $200 to $2,000+ per developer per month depending on session length (the $109.97 session is a heavy day). A team of 8 is roughly
$2k to $18k per month, likely under about 10% of loaded payroll. Long contexts drive cost; the tool itself advises compacting and clearing.

## 5. Local versus cloud break-even (model, not a finding)

Let *C* = monthly cloud spend for the team, *f* = the fraction of the work a local model handles well enough to remove cloud spend,
*K* = local capex, *m* = amortization months, *P* = monthly power and *O* = monthly operator time cost. Local pays off when

`f x C > K/m + P + O`

Illustration (assumptions, not data): team of 20, C = $20k/month; 6 GB10-class boxes at about $4k = $24k, over 36 months about $670/month;
O about $4k/month (a quarter of an engineer); P small. Threshold f is about 25%. The larger the team, the lower the threshold.

What the formula hides, and what this project's evidence says:
1. **f is a quality question.** Earlier experiments here had local models fail a task the cloud model then solved (printf) and cloud models needed for
   the hard diagnosis (MCP tool access). A failed local attempt costs engineer time, which outweighs token savings. Measure f with
   [../research/LOCAL_VS_CLOUD_BENCHMARK.md](../research/LOCAL_VS_CLOUD_BENCHMARK.md) (13 real defect cases with answer keys). No model has been scored yet.
2. **Long contexts.** Agentic work here runs above 150k tokens of context; prompt-processing speed and memory on local hardware are unmeasured and
   could dominate. Measure them, don't assume.
3. **Operating and safety cost.** Local agents need running and containment (see the "blast radius" discussion in the LM Studio case study).
4. A likely design is **routing**: routine work (tests, boilerplate, docs, log triage) to local, hard diagnosis to cloud.
5. An observed claim to test, not a result: the owner reports a newer local model ("Qwen3.8 flash next") approaching cloud-model skill. Score it with the benchmark.

## 6. What to fill in next

- Cumulative real cost of this project (billing page or `/cost` across sessions; only individual sessions are recorded).
- Benchmark results for at least one local and one cloud model, giving *f* and cost per correct fix.
- Measured prompt-processing time on the local hardware at 150k context.
- The actual plan price versus metered equivalent, for the subscription basis.
