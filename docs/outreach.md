# Outreach (STOP C, sai's work)

Nothing here has been sent; nobody has been contacted. This is a template and a list for sai to use.

## Rules for the outreach
- Be honest about what it is: a portfolio project by one engineer, measured on spring-boot, not a product.
- Lead with the maintainer's benefit and their control: **private by default**, comments only if they switch them on.
- Show the real numbers, including the weak spot (public duplicate comments).
- One short message per project, to a maintainer who triages issues (or a "Discussions" post if the project prefers that).
  No follow-up spam: one polite reminder after ~10 days at most.
- Never install the App or comment on a repo without a maintainer's explicit yes.

## Message template (adapt the first line per project)

> **Subject:** A private triage helper for new {project} issues: would a 2-week trial help?
>
> Hi {name},
>
> I noticed {project} gets a steady stream of new issues ({N} open issues have no reply yet). I built a small open-source
> assistant that, when an issue is opened, privately prepares a triage note for maintainers: likely duplicates with a
> similarity score, which details the reporter left out (version, steps, logs), whether a similar issue was fixed in a later
> release, and a draft reply that links only to your own docs.
>
> I measured it on Spring Boot's history (833 issues maintainers had closed as duplicates, replayed in time order): the
> original is in its top 3 for 46% of them, vs 26% for keyword search. It is not good enough to comment publicly on its own
> (duplicates are rare, so false alarms dominate), so by default it never posts anything; you'd see the notes in a small web
> page, and a public comment mode exists only as an opt-in.
>
> Would you be open to a 2-week trial on {project}, read-only? I'd load your public issues and docs, share a link with the
> notes for new issues, and ask for 15 minutes of feedback at the end. Code and evaluation: {repo link}. If it's not useful,
> no worries at all, and thanks for maintaining {project}.
>
> {sai's name}

## Candidate repos (public Java, many open issues without a reply)

Counted with GitHub's issue search on **4 Nov 2026**: open issues, open issues with **0 comments**, and those of them opened since
1 Aug 2026 (recent and still unanswered). Repos that track bugs elsewhere (Jira) are left out. Some repos are missing because the
search API's rate limit cut the run short.

| Repo | Open issues | Open, 0 comments | ... opened since 1 Aug 2026 | Why it fits |
|---|---|---|---|---|
| [spring-projects/spring-ai](https://github.com/spring-projects/spring-ai) | 1,007 | 338 | 67 | Same Spring style as the evaluation; AsciiDoc docs in the repo; fast-moving, many questions |
| [langchain4j/langchain4j](https://github.com/langchain4j/langchain4j) | 679 | 213 | 84 | The library this project uses; many recent unanswered issues; docs in the repo |
| [spring-projects/spring-security](https://github.com/spring-projects/spring-security) | 1,304 | 573 | 43 | Large backlog; the same Spring triage labels and duplicate habits as spring-boot |
| [spring-projects/spring-framework](https://github.com/spring-projects/spring-framework) | 302 | 82 | 23 | Closest to the measured setup (same team conventions) |
| [apache/shardingsphere](https://github.com/apache/shardingsphere) | 234 | 98 | 79 | Most of its unanswered issues are recent: a triage helper would help now |
| [micronaut-projects/micronaut-core](https://github.com/micronaut-projects/micronaut-core) | 461 | 41 | 41 | All unanswered issues are recent; small team |
| [quarkusio/quarkus](https://github.com/quarkusio/quarkus) | 2,437 | 175 | 35 | Very large issue volume; an active triage culture to learn from |
| [spring-cloud/spring-cloud-gateway](https://github.com/spring-cloud/spring-cloud-gateway) | 451 | 119 | 7 | Big backlog of unanswered issues |
| [testcontainers/testcontainers-java](https://github.com/testcontainers/testcontainers-java) | 421 | 67 | 9 | Many "how do I" issues that docs could answer |
| [eclipse-vertx/vert.x](https://github.com/eclipse-vertx/vert.x) | 133 | 46 | 14 | Small enough for a quick, clear trial |
| [OpenAPITools/openapi-generator](https://github.com/OpenAPITools/openapi-generator) | 5,147 | 1,963 | 78 | Huge backlog; many duplicates likely, but very broad (many languages), so a harder fit |

For comparison: spring-projects/spring-boot has 443 open issues, 40 with no reply (7 since 1 Aug): its team triages fast,
which is also why it made a good answer key.

**Suggested first 3:** spring-ai, langchain4j, spring-framework: closest to the measured setup, docs in the repo, and (for the first
two) many recent unanswered issues. The first open-source PR (LangChain4j or Spring AI) is a separate task for sai.
