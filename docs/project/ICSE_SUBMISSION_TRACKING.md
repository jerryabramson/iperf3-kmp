# ICSE 2027 Submission Tracking

Tracking doc for the conference-presentation goal in
[[project_goals_icse_and_monetization]] — targeting
[ICSE 2027](https://conf.researchr.org/home/icse-2027) (Dublin, Ireland;
core conference **28–30 April 2027**, full event 25 April – 1 May 2027).
All dates/criteria below were fetched directly from the official
`conf.researchr.org/track/icse-2027/...` Call for Papers pages on
**2026-08-27** — re-verify against the live pages before acting on anything
here, since CFPs occasionally get amended after initial posting.

## Read this first: the obvious track is already closed

ICSE's main **Research Track** — the one people usually mean by "an ICSE
paper" — had a single submission cycle: abstract due **23 June 2026**,
paper due **30 June 2026**. Both are in the past as of this doc's writing
(today: 2026-08-27). **The Research Track is not an option for ICSE 2027.**
The realistic options are the other tracks below, several of which are
still open, or waiting for ICSE 2028's Research Track cycle instead.

## Full deadline table (source: [Important Dates](https://conf.researchr.org/dates/icse-2027))

| Track | Submission deadline | Notification | Status as of 2026-08-27 |
|---|---|---|---|
| Research Track | Tue 30 Jun 2026 | Tue 20 Oct 2026 | **Closed** |
| New Ideas and Emerging Results (NIER) | Fri 23 Oct 2026 | Fri 18 Dec 2026 | Open (~8 weeks out) |
| Tool Demonstration and Data Showcase | Fri 23 Oct 2026 | Fri 11 Dec 2026 | Open (~8 weeks out) |
| Software Engineering in Practice (SEIP) | Fri 23 Oct 2026 | Fri 11 Dec 2026 | Open (~8 weeks out) |
| Software Engineering Education and Training (SEET) | Fri 23 Oct 2026 | Fri 18 Dec 2026 | Open, not assessed for fit |
| Software Engineering in Society (SEIS) | Fri 23 Oct 2026 | Fri 18 Dec 2026 | Open, not assessed for fit |
| Journal-first Papers | Fri 30 Oct 2026 | Fri 11 Dec 2026 | Open, but see eligibility below — **not realistic for 2027** |
| Industry Challenge | Fri 13 Nov 2026 | Fri 18 Dec 2026 | Open, not assessed for fit |
| Doctoral Symposium | Sat 14 Nov 2026 | Sat 5 Dec 2026 | N/A (not a doctoral candidate submission) |
| SRC (ACM Student Research Competition) | Fri 13 Nov 2026 | Fri 18 Dec 2026 | N/A (student-only) |
| Artifact Evaluation | Fri 29 Jan 2027 | Fri 26 Feb 2027 | Open; only relevant *alongside* an accepted paper |
| Posters | Not yet posted | — | Check closer to date |
| UISE (workshop, not on official track table — [own site](https://uise-workshop.github.io/2027/)) | Thu 27 Nov 2026 | Fri 11 Dec 2026 | Open (~13 weeks out) — confirmed real for 2027 |
| MOBILESoft | *Not listed for 2027* | — | **Not confirmed** — historically co-located every year 2018–2026, absent from ICSE 2027's official co-host list so far; watch for an announcement |

## Journal-First: ruled out for 2027, keep in mind for 2028

Eligibility requires the underlying paper to have **already been accepted**
by a partner journal — IEEE TSE, ACM TOSEM, or EMSE — no earlier than
**11 Oct 2025** and no later than **10 Oct 2026**
([track CFP](https://conf.researchr.org/track/icse-2027/icse-2027-journal-first-papers)).
Nothing from this project is currently submitted to, under review at, or
accepted by any of those three journals, and a from-scratch journal
submission now has no realistic path to acceptance before the 10 Oct 2026
cutoff (typical review cycles run months to well over a year). **Not
pursuable for ICSE 2027.** If a paper from this project is ever accepted by
TSE/TOSEM/EMSE, the same mechanism would apply to whichever ICSE's window
it lands in (ICSE 2028's window will presumably open in the same
roughly-Oct-2026-to-Oct-2027 shape) — worth revisiting then, not now.

## Realistic candidate tracks for 2027 (all deadline Fri 23 Oct 2026)

Three tracks plausibly fit what this project actually has: a real,
working, cross-platform system with a documented engineering story, plus a
research thread on AI-assisted development limits. None of these has been
chosen yet — this is a comparison to decide from, not a decision already
made.

### NIER — New Ideas and Emerging Results

- **Fit**: best match for the `AI_ASSISTED_DEVELOPMENT_LIMITS.md` research
  thread specifically — NIER wants "forward-looking ideas" with promising
  initial results, not a fully mature study, plus a required **"Future
  Plans" section** on developing the idea into a full paper. This project's
  category-3-verification-gap thesis (some correctness only a human can
  check) is exactly this shape: a real idea with real supporting evidence
  from this repo's own history, not yet a full empirical study.
- Max **4 pages** main text + 1 page references. IEEE conference template
  (10pt/24pt).
- **Double-anonymous** review — no author names, third-person
  self-citation, ORCID required for accepted papers.
- [CFP](https://conf.researchr.org/track/icse-2027/icse-2027-new-ideas-and-emerging-results--nier-)

### Tool Demonstration and Data Showcase

- **Fit**: best match for the platform-independence/code-sharing
  engineering story itself (`docs/project/MVP_MATURITY_ASSESSMENT.md`, the
  professor-facing `.docx`) — this track exists specifically to show off a
  real tool, not argue a research claim.
- Max **4 pages**, IEEE template. **Single-anonymous** (author identities
  included) — no anonymization work needed for this one specifically.
- **Mandatory 3–5 minute demo video**, hosted on YouTube, with voice-over/
  annotation.
- Tool must be reviewer-runnable without building from source — packaged
  distributable (website, VM, Docker, or system config). This project
  already has real installers (`.dmg`/`.deb`, CLAUDE.md's packaging
  section) — closer to ready than most submissions would be.
- [CFP](https://conf.researchr.org/track/icse-2027/icse-2027-demonstrations)

### SEIP — Software Engineering in Practice

- **Fit**: best match if the submission leans into the *engineering*
  narrative — cross-platform code sharing despite a large multi-threaded
  native dependency (Sections 4–6 of the `.docx`), told as an
  industrially-relevant practice report rather than a research claim.
- Max **10 pages** main text + 2 pages references — the most room of the
  three.
- **Single-anonymous** review (SEIP explicitly does not require
  double-anonymous, since author/organization visibility is often
  relevant to this track).
- Scope wants: a practical problem, investigation methodology/context,
  evidence, technical/empirical soundness — a good match for the
  case-study documentation this repo already does as a matter of habit.
- [CFP](https://conf.researchr.org/track/icse-2027/icse-2027-seip)

**Not assessed yet**: SEET (education-focused — possible fit given the
course origin/teaching angle, not evaluated) and SEIS (societal-impact
focus — likely weak fit, not evaluated in depth).

### UISE — User Interface and Experience for Software Engineering (workshop)

Not on the main track table above — it's an ICSE 2027 **workshop**, with
its own site and later deadline. Confirmed real for 2027 (own live CFP
site), unlike MOBILESoft below.

- **Fit**: arguably the strongest match found so far — scope explicitly
  covers cross-platform UI/UX and cross-platform testing, plus
  AI-enhanced UI tooling. Maps directly onto this project's Compose
  Multiplatform work (the extensive per-form-factor UI tuning already
  done — phones, camera notches, tablets, desktop, both orientations) and
  can pull in the AI-assisted-development thread too if framed as
  AI-tooling-for-cross-platform-UI-work.
- **Deadline: Thu 27 November 2026** (23:59 AoE) — later than the Oct 23
  cluster, more runway. Notification 11 Dec 2026, camera-ready 29 Jan 2027.
- **Full papers**: 8 pages including references. **Short papers**: 5 pages.
  Single-anonymous review, submitted via HotCRP, PDF, English.
- Topics: AI-enhanced UI/UX (generation, intelligent GUI testing,
  accessibility, personalization), UI/UX-centric SE (code analysis,
  **cross-platform testing**, requirements/accessibility engineering), and
  benchmarks/emerging work (datasets, multimodal systems, HCI-SE
  integration).
- [Workshop site](https://uise-workshop.github.io/2027/) (contact:
  uiseworkshop@gmail.com)

### MOBILESoft — not confirmed for ICSE 2027, watch for an announcement

MOBILESoft (Mobile Software Engineering and Systems) co-located with ICSE
for 9 consecutive years (2018–2026 per its own
[conference-series page](https://conf.researchr.org/series/mobilesoft)) and
has historically run both a Research Track and an App Track — the App
Track in particular would be a strong intersection of this project's two
goals (a real mobile app, presented at a research venue). **But it is
absent from ICSE 2027's own official co-hosted-conferences list**
(AST/CAIN/CHASE/FORGE/FormaliSE/ICPC/MSR/SEAMS/TechDebt — no MOBILESoft),
and MOBILESoft's own series page has no 2027 edition posted as of
2026-08-27. This could simply mean it hasn't been organized/announced yet
— the pattern is strong — but **don't plan around it as confirmed**; check
back periodically for a MOBILESoft 2027 announcement rather than assuming
one.

## Cross-track requirements to plan for regardless of track chosen

- **AI/LLM usage disclosure.** Explicitly required by the Research Track's
  CFP (ACM/IEEE authorship policy) — using an AI tool to generate content
  (text, code, tables, citations) must be disclosed; using it as a
  Grammarly-style editing aid does not need disclosure; AI cannot be
  listed as an author. **This is directly relevant to this project** —
  Claude Code was used extensively throughout its development. Only
  confirmed stated on the Research Track's own CFP page so far; likely
  applies conference-wide under the same ACM/IEEE policies but **not yet
  independently confirmed on NIER/Tool-Demo/SEIP's own CFP pages** —
  verify before submitting.
- **Reference integrity.** Research Track's CFP explicitly warns
  submissions are checked for hallucinated/fabricated/unverifiable
  references, with automatic desk rejection on confirmed cases — sensible
  default to hold any track's submission to, given this project's own
  documented AI-verification thesis.
- **In-person attendance.** Every track checked requires the presenting
  author to register for and attend the conference in person (virtual only
  if the conference format itself changes) — factor real travel cost/time
  to Dublin into the decision, not just the writing effort.
- **ORCID ID** required for accepted-paper authors across tracks.
- **Human-subjects/ACM compliance declaration** — SEIP's CFP calls this out
  explicitly; likely moot for this project (no human-subjects research
  performed), but confirm before submitting.

## Open decisions / TODO

- [ ] **Pick a track** (or more than one, for different material) — NIER
      for the research thesis, Tool Demo for the working system, SEIP for
      the engineering-practice narrative, and UISE (workshop, later
      deadline) for the cross-platform-UI angle are the real candidates;
      nothing chosen yet.
- [ ] **Check back for a MOBILESoft 2027 announcement** — if it does
      co-locate again as it has every year since 2018, its App Track could
      be the single best intersection of the two project goals (research
      venue + real mobile app), worth revisiting once/if confirmed.
- [ ] **Decide authorship** — solo, or with an instructor/advisor
      credited — not yet discussed.
- [ ] If NIER (double-anonymous) is chosen: plan what needs anonymizing —
      this repo's own docs are full of personal names, contact details, and
      institutional affiliation, plus direct links to the (currently private) GitHub repo;
      a double-anon submission can't casually link back to identifying
      material during review.
- [ ] If Tool Demo is chosen: produce the mandatory 3–5 minute demo video
      and confirm the packaged builds are truly reviewer-runnable
      out-of-the-box (no local `local.properties`/SDK setup assumptions
      baked in).
- [ ] Draft an outline/abstract for whichever track(s) chosen, working
      backward from the **23 October 2026** deadline (~8 weeks from this
      doc's writing).
- [ ] Confirm the AI-disclosure requirement's exact wording on whichever
      track's own CFP is ultimately used, rather than assuming the
      Research Track's language carries over verbatim.
