# The problem

Facts checked on 2-3 Nov 2026 with the GitHub API and by reading issues. Read this before
changing the design: it says who we build for and how we prove it works.

## In one line
Maintainers spend minutes on every new issue finding duplicates, labelling and asking for
missing details; small teams fall behind, and nothing tells them privately, with a score,
"this is probably #N".

## Why I chose this problem
I listed about 60 real-world problems my stack (Java, Spring Boot, Kafka, Postgres) could
help with, across government, fintech and climate. I shortlisted 6 and checked each for two
things: is there public data, and can I measure whether a solution works? Two survived:
verifying satellite wildfire alerts (NASA FIRMS) and duplicate-issue triage. I dropped the
wildfire idea because the ground truth sits with forest departments, so I couldn't prove
accuracy on my own. I'd also felt the triage pain myself: in my team, people often raised
issues that were already reported, and it took days to reproduce one and confirm
"duplicate, already fixed". On GitHub, spring-boot alone has over 2,000 issues that
maintainers marked as duplicates by hand, a public answer key I can measure against.

## Who has the pain?
Maintainers of popular open-source repos. Two kinds:
- **Large paid teams** (e.g. spring-projects/spring-boot). Triage is fast: in 3 duplicates
  we read, a person marked each one within 3-20 hours. The cost is attention: someone reads,
  searches and links every issue by hand.
- **Small volunteer teams** (e.g. langchain4j/langchain4j). Issues wait. The oldest open
  issues with zero comments date from 21 Aug 2023 (#121), 9 Oct 2023 (#213) and
  1 Feb 2024 (#582), over 2.5 years without a reply.

## How often?
- spring-boot has **2,011 issues labelled `status: duplicate`** (GitHub search, 3 Nov 2026).
  Every one was found and linked by a person.
- langchain4j uses a `duplicate` label (count not taken yet).

## What do they do today?
1. Read the new issue.
2. Search old issues by hand (the duplicates we read used similar wording to the original).
3. Comment "Duplicate of #N", add the duplicate label, close with reason `duplicate`.
GitHub stores this: `state=closed`, `state_reason=duplicate` (seen on #52529).

## What's missing?
- GitHub's built-in duplicate suggestion only appears **while the reporter types**. A
  maintainer said: "If the issue gets created despite the warning, I'm none the wiser"
  (github.com/orgs/community/discussions/199395).
- AI bots that post publicly annoy people: users and maintainers want to block Copilot-style
  bots (github.com/orgs/community/discussions/197931). So we report **privately to the
  maintainer** (shadow mode) by default; public comments are opt-in.
- GitHub's agentic triage template (gh-aw) can label and list possible duplicates, but no
  tool we found publishes **measured accuracy**.

## What number proves the fix works?
- **Duplicate recall@3:** in a time-ordered replay over spring-boot's labelled duplicates,
  how often the real original is in our top 3, vs a keyword-search baseline (Postgres
  full-text).
- **Precision at our threshold:** how often a "likely duplicate" we flag is right (so we
  don't add noise).
- **Label match:** suggested labels vs the labels maintainers actually added.
- **Maintainer minutes saved** (estimate) and **cost per issue in Rs**.

## Decisions that follow
- **Answer key:** spring-boot (2,011 labelled duplicates). Checked in M1: only 178 have
  `state_reason=duplicate`, so the label is the main signal; 1,036 have a reliable link to the original.
- **Real users to approach:** smaller Java repos such as LangChain4j, where issues wait.
- **Default output:** a private triage report, not a public comment.
- **Progressive autonomy:** 1) shadow (private report, default) -> 2) suggest (opt-in comment with score) -> 3) act (auto-label or close only above a maintainer-set threshold). The bot earns each step with measured precision.

## Known limits (say these honestly)
- Maintainers mostly catch duplicates that *look* alike; ones in different words may never
  get labelled, so the answer key leans towards easier cases.
- Old closed duplicates don't say `state_reason=duplicate`: GitHub shows `completed` on them
  (and 2023-24 ones are often `not_planned`), not NULL. So the `status: duplicate` label is the
  main signal (checked on real data in M1, see docs/m1-report.md).
- Our 3-issue sample is tiny; the replay over all labelled duplicates is the real test.
