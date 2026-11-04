# Demo video script (2 minutes)

For sai to record at STOP B. Screen: the demo page (`http://<host>/` or `http://localhost:8080/`), one browser window, 1280×800.

## The three duplicate pairs to show

Real spring-boot duplicates from the answer key (a maintainer closed the new issue as a duplicate of the original). In each,
**the new issue's text never mentions or links the original's number**, so the match comes from meaning, not from a link.
The original is the top result in our search, in a time-ordered setting (only issues that existed before).

| # | New issue | Original (found as top match) | Similarity | Why it's a good demo |
|---|---|---|---|---|
| 1 | [#52515](https://github.com/spring-projects/spring-boot/issues/52515) Support Optional<NestedClass> in @ConfigurationProperties (Oct 2026) | [#15999](https://github.com/spring-projects/spring-boot/issues/15999) Optional nested configuration property not assigned (Feb 2019, closed as completed) | 80.4%, rank 1 | Different words, **7 years apart**; nobody would remember #15999 |
| 2 | [#51234](https://github.com/spring-projects/spring-boot/issues/51234) Spring Boot 4.1.0 MongoDB URI authentication not applied (Aug 2026) | [#50767](https://github.com/spring-projects/spring-boot/issues/50767) spring.data.mongodb.uri credentials ignored — MongoClient created with credential=null in Spring Boot 4.0.6 (Jun 2026, closed as not planned) | 84.9%, rank 1 | Same bug described from two angles (symptom vs cause) |
| 3 | [#47624](https://github.com/spring-projects/spring-boot/issues/47624) Spring Boot 4.0.0-M3 does not configure the Jackson Kotlin Module (Oct 2025) | [#47485](https://github.com/spring-projects/spring-boot/issues/47485) Automatically detect Jackson 3 modules (Oct 2025, closed as completed) | 81.3%, rank 1 | A specific symptom matched to the general fix; may show the "already fixed?" hint |

Checked on 4 Nov 2026 with the live search (`GET /repos/spring-projects/spring-boot/issues/{n}/similar`). How they were picked:
answer-key pairs since #30000 from external reporters whose title and body don't contain the original's number (256 pairs),
then kept those with the original in the top 3, then three with clearly different wording.

**Before recording:** open each pair once with the real model so the reports exist (the page's **Create report** button,
or `curl -X POST localhost:8080/reports/spring-projects/spring-boot/51234`), and make sure the day's token budget isn't used up
(the page would show "AI checks paused").

## Script (about 2 minutes)

**0:00–0:15 · The problem.**
"Open-source maintainers spend hours on new issues: is this a duplicate, what's missing, where's the doc? Spring Boot alone has
over 43,000 issues. I built an assistant that tells a maintainer, privately and within seconds, what it found."

**0:15–0:35 · How it works (one sentence per box; show the README diagram).**
"A GitHub webhook comes in, it's saved with an outbox in Postgres and sent through Kafka to a worker. The worker embeds the
issue, searches earlier issues with pgvector, asks Claude two narrow questions, and stores a report."

**0:35–1:05 · Pair 1.** Open `/?repo=spring-projects/spring-boot&number=52515`.
"This issue from October 2026 asks for Optional nested properties. The assistant's best match is #15999, from 2019: the same
problem in different words, already fixed. The report also says what details are missing and drafts a reply the maintainer can
edit and copy."

**1:05–1:25 · Pair 2 or 3, quickly.** Open #51234 (or #47624 if the "already fixed?" box shows).
"Another real duplicate: the new issue describes the symptom, the old one the cause. The similarity is 85%; the assistant shows it
as a possible duplicate and lets the maintainer decide."

**1:25–1:50 · The numbers, honestly.**
"I measured it on 833 duplicates that maintainers had confirmed, replaying history: the original is in the top 3 for 46%, versus
26% for keyword search. Public comments are harder: duplicates are only 4% of new issues, so even with Claude checking, most
comments would be wrong. So it runs in shadow mode by default, and comment mode is opt-in, at 90% plus Claude's agreement."

**1:50–2:00 · Close.**
"Java 21, Spring Boot, Kafka, Postgres with pgvector, Claude Haiku, running on a free ARM VM with k3s. The code, the evaluation
and the design notes are on GitHub."
