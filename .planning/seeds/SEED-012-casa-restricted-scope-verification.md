---
id: SEED-012
status: dormant
planted: 2026-05-14
planted_during: Phase 6 launch-readiness - CASA deferred to post-launch track
trigger_when: "when Vietnam beta exceeds 100 users OR when the CASA submission is funded/scheduled"
scope: large
---

# SEED-012: CASA Restricted-Scope Verification (Post-Launch Track)

## Why This Matters

Google's CASA (Cloud Application Security Assessment) restricted-scope verification is required to move the OAuth consent screen from Testing to Production. Phase 6 launched into Testing mode (100-user cap), which is sufficient for the Vietnam beta but caps growth. CASA is a 4-12 week external process: lab engagement, evidence package, Letter of Assessment, then the consent-screen move. Until CASA closes, FND-07 stays Pending in REQUIREMENTS.md and Zero Mail cannot accept user 101.

## When to Surface

Surface this seed when Vietnam beta growth exceeds 100 users or when the CASA submission is funded and scheduled. The trigger mirrors the frontmatter so this post-launch track stays dormant until the project is ready to move the OAuth consent screen beyond Testing.

## Scope Estimate

**Large.** The work spans external lab coordination, evidence preparation, and a 4-12 week wall-clock verification window.

## Required Evidence Package

- Privacy policy URL that explicitly addresses restricted Gmail scopes.
- Demo video showing read messages, modify labels, and save drafts in use.
- Data-flow diagram showing Gmail data flow and privacy boundaries.
- MFA evidence for Google Cloud Console, OpenRouter, SePay, and repo admin access.
- Key-rotation evidence for the AES-GCM refresh-token key plus last-rotated date.
- Employee-access policy describing who can access prod data and how.
- Penetration test report, with the exact CASA tier clarified with the chosen lab.
- Software bill of materials generated from Gradle dependencies and the frontend package inventory.

## Candidate CASA Labs

- CREST-accredited lab such as Bishop Fox or another similarly accredited provider.
- Bishop Fox specifically, since it is a common CASA pick in the research notes.
- TBD - final pick when budget and timeline are committed.

## Safety Rules

- No data shared with the CASA lab includes real user emails, prompts, or completions.
- Demo accounts use synthetic data only.
- Lab access to source code is read-only.

## Closure Trigger

Seed closes when (1) Letter of Assessment is received from the CASA lab AND (2) Google OAuth consent screen status flips from `Testing` to `Production` in the Google Cloud Console AND (3) FND-07 in `.planning/REQUIREMENTS.md` is updated from `Pending` to `Complete` with the LoA date. At that point this seed file moves from `.planning/seeds/` to an archive location (TBD) and `LAUNCH-GO-NOGO.md` item (h) is updated to reflect launch mode = `Production`.

## Breadcrumbs

- `.planning/REQUIREMENTS.md` - FND-07, currently Pending, flips to Complete on closure.
- `.planning/research/PITFALLS.md` - Pitfall 1: Restricted-scope OAuth verification.
- `.planning/LAUNCH-GO-NOGO.md` - item (h), which this seed eventually unblocks.
