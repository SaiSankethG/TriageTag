# M4 results: the LLM layer with a real model (STOP A)

4 Nov 2026. Model: **Claude Haiku 5.5** (`claude-haiku-5-5`, $0.10 / $0.50 per million input / output tokens), thinking
off, max 1,024 output tokens, through `langchain4j-anthropic` 1.22.0. Generated files: `docs/m4-duplicate-check.md`
(eval output) and `docs/m4-scoring.md` (30 reports for hand-scoring).

## 1. Comment-mode rule: score alone vs score + LLM "same root problem?" (sai's decision 3)

Replay on spring-boot, **external reporters only**: 720 scored duplicates, and the 1,000 random non-duplicates of the M3
false-alarm sample. Threshold 0.825 on the vector top-1 score. The LLM checks the top match only when it passes the threshold
(204 calls, 247,819 tokens, 1 answer with invalid JSON twice → counted as "no").

| | Score only | Score + LLM "yes" |
|---|---|---|
| Duplicates flagged (of 720) | 103 | 75 |
| ... top match is the true original (a correct comment) | **55** (7.6%) | **48** (6.7%) |
| ... top match is another issue | 48 | 27 |
| Non-duplicates flagged = false alarms (of 1,000) | **101 (10.1%)** | **46 (4.6%)** |

- **The LLM check halves the false alarms** (10.1% → 4.6%) and **keeps 48 of the 55 correct flags (87%)**.
- Among flagged duplicates, the share whose top match is the true original rises from 53% (55/103) to 64% (48/75).

### What it would mean for public comments (an estimate)
External issues are mostly not duplicates: 720 marked duplicates among 17,685 external issues (4.1%). Per 1,000 new external
issues, that gives:

| | Comments posted | Correct (true original) | Correct share |
|---|---|---|---|
| Score ≥ 0.825 only | ~103 | ~3.1 | **~3%** |
| Score ≥ 0.825 + LLM "yes" | ~48 | ~2.7 | **~6%** |

**At 0.825, even with the LLM check, most public comments would point at the wrong issue.** Caveat: "non-duplicate" means
"not marked as a duplicate by a maintainer". Some flagged "false alarms" are real but unmarked duplicates, and some "other
issue" matches are the same problem filed elsewhere, so the true share is higher than this. That can only be checked by
reading them. Comment mode (M5) needs a stricter threshold or more checks; shadow mode (private top 3) stays the default.
See open question 1 in docs/m4-report.md.

## 2. 30 recent issues for hand-scoring

`docs/m4-scoring.md`: the 30 most recent spring-boot issues by external reporters, each with its full report (88,554 tokens).
Quick counts before sai's scoring:
- **Draft replies**: 22 written, 8 "nothing relevant found" (no source above the thresholds, so no LLM call). No draft
  cites a source as "[n]" (prompt v2). One draft contains a link that isn't a source URL: a wiki link it found inside a
  docs section's text. The check flagged it.
- **LLM duplicate check** ran for 2 of 30 (top score ≥ 0.825): #52500 → #52499 (0.98, near-identical twins filed
  back to back; LLM said **UNSURE**), #52465 → #51843 (0.85, LLM: **NO**).
- **Labels** suggested for 8 of 30; **missing details** flagged for 20 of 30 (10 had nothing missing).

## 3. Cost

All LLM calls on 4 Nov (smoke tests + eval + 30 reports): **339,156 tokens in 259 calls**, logged in `llm_usage`.
That's at most $0.17 if every token were output, and about $0.04–0.06 at the real mix (mostly input). The daily budget for these runs was set to 1.5M tokens.
One full report costs about 1,000–5,600 tokens (≈ $0.0002–0.001).

## 4. M5 step 1: stricter thresholds before comment mode (4 Nov 2026)

One new run of the duplicate-check eval (`docs/m5-threshold-check.md`): the LLM is asked once per issue at the lowest threshold
(0.825), and the stricter thresholds reuse those answers. 204 calls, 248,173 tokens (≈ $0.03). The LLM's answers vary a
little between runs: at 0.825 this run confirmed 48 false alarms, the M4 run 46.

| Threshold | Correct flags (score + LLM, of 720) | False alarms (score + LLM, of 1,000) | ... of which forward-/back-port clones | False alarms without clones |
|---|---|---|---|---|
| 0.825 | 50 | 48 (4.8%) | 21 | 27 (2.7%) |
| 0.85 | 33 | 36 (3.6%) | 18 | 18 (1.8%) |
| **0.90** | **19** | **25 (2.5%)** | **15** | **10 (1.0%)** |

Per 1,000 new external issues (720 of 17,685 are marked duplicates, 4.1%): share of public comments that would point at the
marked original.

| Threshold | All issues | Not commenting on clone issues |
|---|---|---|
| 0.825 | ~6% | ~9% |
| 0.85 | ~5% | ~9% |
| 0.90 | ~4% | ~10% |

What this says:
- **A stricter threshold alone doesn't make comments more precise.** It removes correct flags about as fast as false alarms,
  because the false alarms that are left score high: most are the team's **forward-port / back-port clones** (an issue
  copied to another branch, consecutive numbers, identical title). The LLM rightly calls them the same problem. They are
  not "duplicates" in the answer key because nobody closes them as such.
- **Comment mode must never comment on clones**: they are opened by the team and carry `status: forward-port` /
  `status: back-port` from the start. M5 step 4 adds a per-repo "never comment on issues with these labels" list for that.
- Even then, about 1 in 10 public comments would point at the marked original. Some of the rest are real duplicates that
  were never marked; the table below lets sai check how many.
- **Decision applied (per the plan):** the default comment threshold is now the strictest tested value, **0.90**, and comment
  mode stays **off by default** (shadow mode). Public comments need score ≥ 0.90 AND the LLM's "yes".

### For sai: the 48 confirmed false alarms at 0.825, to read by hand
Non-duplicates (not marked as duplicates by maintainers) whose top match scored ≥ 0.825 and which the LLM called the same root
problem. Sorted by score. Put **yes / no** in the last column: is it really a duplicate of the matched issue? Clones are marked.

| # | New issue | Matched earlier issue | Score | Clone? | Real duplicate? (sai) |
|---|---|---|---|---|---|
| 1 | [#35151](https://github.com/spring-projects/spring-boot/issues/35151) Adapt Paketo system tests to changes in the Tomcat buildpack | [#35149](https://github.com/spring-projects/spring-boot/issues/35149) Adapt Paketo system tests to changes in the Tomcat buildpack | 1.000 | yes |  |
| 2 | [#43993](https://github.com/spring-projects/spring-boot/issues/43993) Upgrade to Appengine Sdk 1.9.98 | [#35462](https://github.com/spring-projects/spring-boot/issues/35462) Upgrade to AppEngine SDK 1.9.98 | 1.000 |  |  |
| 3 | [#39772](https://github.com/spring-projects/spring-boot/issues/39772) Remove obsolete JDK 9 check in spring-boot-smoke-test-jersey | [#39771](https://github.com/spring-projects/spring-boot/issues/39771) Remove obsolete JDK 9 check in spring-boot-smoke-test-jersey | 0.997 | yes |  |
| 4 | [#3994](https://github.com/spring-projects/spring-boot/issues/3994) velocity template can't use "spring.velocity.charset" | [#3993](https://github.com/spring-projects/spring-boot/issues/3993) velocity template can't use "spring.velocity.charset" | 0.996 |  |  |
| 5 | [#35365](https://github.com/spring-projects/spring-boot/issues/35365) Can't use PEM encoded PKCS#8 EC keys with server.ssl.certificate-private-key | [#35364](https://github.com/spring-projects/spring-boot/issues/35364) Can't use PEM encoded PKCS#8 EC keys with server.ssl.certificate-private-key | 0.996 | yes |  |
| 6 | [#49955](https://github.com/spring-projects/spring-boot/issues/49955) Outdated dependencies detected in package Spring Boot | [#47332](https://github.com/spring-projects/spring-boot/issues/47332) Outdated dependencies detected in package Spring Boot | 0.994 |  |  |
| 7 | [#37529](https://github.com/spring-projects/spring-boot/issues/37529) Remove Java 20 from CI | [#37528](https://github.com/spring-projects/spring-boot/issues/37528) Remove Java 20 from CI | 0.994 | yes |  |
| 8 | [#35690](https://github.com/spring-projects/spring-boot/issues/35690) Description of spring.data.mongodb.uri property incorrectly states that it overrides spring.data.mongodb.database | [#35689](https://github.com/spring-projects/spring-boot/issues/35689) Description of spring.data.mongodb.uri property incorrectly states that it overrides spring.data.mongodb.database | 0.991 | yes |  |
| 9 | [#41850](https://github.com/spring-projects/spring-boot/issues/41850) Document the need to explicitly reset mock servers when using mock server customizers directly | [#41849](https://github.com/spring-projects/spring-boot/issues/41849) Document the need to explicitly reset mock servers when using mock server customizers directly | 0.989 | yes |  |
| 10 | [#41193](https://github.com/spring-projects/spring-boot/issues/41193) Document tracing support for RestClient | [#41192](https://github.com/spring-projects/spring-boot/issues/41192) Document tracing support for RestClient | 0.987 | yes |  |
| 11 | [#36113](https://github.com/spring-projects/spring-boot/issues/36113) Improve documentation for baggage propagation into MDC | [#36112](https://github.com/spring-projects/spring-boot/issues/36112) Improve documentation for baggage propagation into MDC | 0.983 | yes |  |
| 12 | [#31823](https://github.com/spring-projects/spring-boot/issues/31823) Publishing a docker image to a private registry fails without authentication | [#31821](https://github.com/spring-projects/spring-boot/issues/31821) Publishing a docker image to a private registry fails without authentication | 0.982 |  |  |
| 13 | [#34660](https://github.com/spring-projects/spring-boot/issues/34660) EmbeddedWebServerFactoryCustomizerAutoConfiguration should not run when embedded web server is not configured | [#34659](https://github.com/spring-projects/spring-boot/issues/34659) EmbeddedWebServerFactoryCustomizerAutoConfiguration should not run when embedded web server is not configured | 0.980 | yes |  |
| 14 | [#20130](https://github.com/spring-projects/spring-boot/issues/20130) JSON keys containing a dot from CF environment are not handled as a single path segment | [#20129](https://github.com/spring-projects/spring-boot/issues/20129) JSON keys containing a dot from CF environment are not handled as a single path segment | 0.980 | yes |  |
| 15 | [#38108](https://github.com/spring-projects/spring-boot/issues/38108) Document that properties that change an application's beans are fixed at build time | [#38107](https://github.com/spring-projects/spring-boot/issues/38107) Document that properties that change an application's beans are fixed at build time | 0.979 | yes |  |
| 16 | [#31822](https://github.com/spring-projects/spring-boot/issues/31822) Publishing a docker image to a private registry fails without authentication | [#31821](https://github.com/spring-projects/spring-boot/issues/31821) Publishing a docker image to a private registry fails without authentication | 0.959 |  |  |
| 17 | [#30437](https://github.com/spring-projects/spring-boot/issues/30437) Sort entries in additional-spring-configuration-metadata.json | [#30436](https://github.com/spring-projects/spring-boot/issues/30436) Sort entries in additional-spring-configuration-metadata.json | 0.958 | yes |  |
| 18 | [#30782](https://github.com/spring-projects/spring-boot/issues/30782) Document support for Java 18 | [#30781](https://github.com/spring-projects/spring-boot/issues/30781) Document support for Java 18 | 0.956 | yes |  |
| 19 | [#33327](https://github.com/spring-projects/spring-boot/issues/33327) Actuator document is misleading about k8s startup probe | [#33326](https://github.com/spring-projects/spring-boot/issues/33326) Actuator document is misleading about k8s startup probe | 0.950 | yes |  |
| 20 | [#43340](https://github.com/spring-projects/spring-boot/issues/43340) NoClassDefFoundError when using JUnit to test a Gradle 7.6.x app that depends on spring-boot-actuator-autoconfigure but not on org.junit.platform:junit-platform-launcher | [#43339](https://github.com/spring-projects/spring-boot/issues/43339) java.lang.NoClassDefFoundError: org/junit/platform/launcher/TestExecutionListener & java.lang.ClassNotFoundException: org.junit.platform.launcher.TestExecutionListener | 0.942 |  |  |
| 21 | [#27973](https://github.com/spring-projects/spring-boot/issues/27973) spring-boot-maven-plugin can support use timezone for  build info properties 'time' | [#15201](https://github.com/spring-projects/spring-boot/issues/15201) spring-boot-maven-plugin build info with timezone | 0.929 |  |  |
| 22 | [#29886](https://github.com/spring-projects/spring-boot/issues/29886) MongoProperties uri  and host/port validation conflicts (again) with EmbeddedMongoAutoConfiguration when having uri set | [#8219](https://github.com/spring-projects/spring-boot/issues/8219) MongoProperties uri and host/port validation conflicts with EmbeddedMongoAutoConfiguration when having uri set | 0.916 |  |  |
| 23 | [#24596](https://github.com/spring-projects/spring-boot/issues/24596) Document warning about `InMemoryOAuth2AuthorizedClientService` | [#24595](https://github.com/spring-projects/spring-boot/issues/24595) Document warning about InMemoryOAuth2AuthorizedClientService | 0.907 | yes |  |
| 24 | [#18467](https://github.com/spring-projects/spring-boot/issues/18467) "factory already defined" error with spring-boot-maven-plugin in maven multi-module application | [#10617](https://github.com/spring-projects/spring-boot/issues/10617) Tomcat fails to start with "factory already defined" error if two integration test harnesses start spring boot as part of the same maven multi-module build (JVM instance) | 0.901 |  |  |
| 25 | [#24978](https://github.com/spring-projects/spring-boot/issues/24978) Add auto-configuration for Micrometer's Dynatrace v2 meter registry | [#14522](https://github.com/spring-projects/spring-boot/issues/14522) Auto-configure Micrometer's Dynatrace meter registry | 0.900 |  |  |
| 26 | [#41283](https://github.com/spring-projects/spring-boot/issues/41283) PaketoBuilderTests fail due to deprecation warning from the ca-certificates buildpack | [#41282](https://github.com/spring-projects/spring-boot/issues/41282) PaketoBuilderTests fail due to deprecation warning from the ca-certificates buildpack | 0.889 | yes |  |
| 27 | [#8778](https://github.com/spring-projects/spring-boot/issues/8778) ClassNotFoundException for org.elasticsearch.cluster.health.ClusterHealthStatus | [#5106](https://github.com/spring-projects/spring-boot/issues/5106) Healthcheck fails with Elasticsearch 2.2.0 | 0.882 |  |  |
| 28 | [#9619](https://github.com/spring-projects/spring-boot/issues/9619) upgrade build-helper-maven-plugin | [#6301](https://github.com/spring-projects/spring-boot/issues/6301) Upgrade to Build Helper Maven Plugin 1.10 | 0.878 |  |  |
| 29 | [#23864](https://github.com/spring-projects/spring-boot/issues/23864) Spring Boot can't be checked out on Windows without enabling git core.longpaths | [#10868](https://github.com/spring-projects/spring-boot/issues/10868) Can't checkout project on Windows due to very long file name | 0.875 |  |  |
| 30 | [#21102](https://github.com/spring-projects/spring-boot/issues/21102) Test classes annotated with @SpringBootTest with different args attributes share a context when they should not | [#20866](https://github.com/spring-projects/spring-boot/issues/20866) Test classes annotated with @SpringBootTest with different args attributes share a context when they should not | 0.874 | yes |  |
| 31 | [#23617](https://github.com/spring-projects/spring-boot/issues/23617) Large files throw YAML exceptions | [#23096](https://github.com/spring-projects/spring-boot/issues/23096) Large files can throw YAMLException due to node limits | 0.869 |  |  |
| 32 | [#33856](https://github.com/spring-projects/spring-boot/issues/33856) Failing calls to reactive health indicators are not logged | [#17664](https://github.com/spring-projects/spring-boot/issues/17664) Reactive health indicators do not log health check failure | 0.869 | yes |  |
| 33 | [#48653](https://github.com/spring-projects/spring-boot/issues/48653) @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT) is no longer applied to the management server | [#14682](https://github.com/spring-projects/spring-boot/issues/14682) ApplicationContext Failure with @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT) when Management Port Specfied | 0.866 |  |  |
| 34 | [#35723](https://github.com/spring-projects/spring-boot/issues/35723) Hibernate Schema Validation filter is not working after upgrading to Spring Boot >3 (v3.0.1 - v3.1) | [#33832](https://github.com/spring-projects/spring-boot/issues/33832) Hibernate Schema Validation filter is not working after upgrading to Spring Boot v3.0.1 | 0.866 |  |  |
| 35 | [#38348](https://github.com/spring-projects/spring-boot/issues/38348) @MockBean annotation does not find existing bean created through @FeignClient | [#21565](https://github.com/spring-projects/spring-boot/issues/21565) @MockBean for a @FeignClient not working as it did in 2.2.5 | 0.862 |  |  |
| 36 | [#5071](https://github.com/spring-projects/spring-boot/issues/5071) @AuthenticationPrincipal not working with devtools enabled | [#4649](https://github.com/spring-projects/spring-boot/issues/4649) devtools and spring-session problem | 0.858 |  |  |
| 37 | [#32410](https://github.com/spring-projects/spring-boot/issues/32410) Enable PaketoBuilderTests on Java 18 | [#32296](https://github.com/spring-projects/spring-boot/issues/32296) Enable PaketoBuilderTests on Java 18 | 0.849 | yes |  |
| 38 | [#30115](https://github.com/spring-projects/spring-boot/issues/30115) Document when config data properties are invalid | [#30113](https://github.com/spring-projects/spring-boot/issues/30113) Document when config data properties are invalid | 0.843 | yes |  |
| 39 | [#11987](https://github.com/spring-projects/spring-boot/issues/11987) Actuator doesn't use the CORS Configuration with default security configuration and Spring MVC | [#9548](https://github.com/spring-projects/spring-boot/issues/9548) Actuator doesn't use the CORS Configuration with default security config | 0.841 |  |  |
| 40 | [#2483](https://github.com/spring-projects/spring-boot/issues/2483) Basic Auth realm not set from security.basic.realm | [#2466](https://github.com/spring-projects/spring-boot/issues/2466) Spring Boot ManagementSecurityAutoConfiguration instantiating two realms by default | 0.839 |  |  |
| 41 | [#35658](https://github.com/spring-projects/spring-boot/issues/35658) RestTemplate builder: HttpComponentsClientHttpRequestFactory has the setReadTimeout method marked as deprecated | [#32857](https://github.com/spring-projects/spring-boot/issues/32857) Setting a RestTemplateBuilder read timeout results in an IllegalStateException due to deprecations in HttpComponentsClientHttpRequestFactory | 0.837 |  |  |
| 42 | [#17878](https://github.com/spring-projects/spring-boot/issues/17878) Bypass Spring Security authentication for remote devtools endpoint | [#3889](https://github.com/spring-projects/spring-boot/issues/3889) Remote devtools  does not work with Spring Security | 0.836 |  |  |
| 43 | [#28938](https://github.com/spring-projects/spring-boot/issues/28938) Actuator Info from application configuration missing after update to 2.6 | [#28933](https://github.com/spring-projects/spring-boot/issues/28933) Info Actuator - Custom Properties not returned - Spring Boot 2.6.0-2.6.1 | 0.835 |  |  |
| 44 | [#31064](https://github.com/spring-projects/spring-boot/issues/31064) SPRING BOOT UPGRADE CAUSES ISSUE WHILE STARTING UP | [#24880](https://github.com/spring-projects/spring-boot/issues/24880) Upgrade to 2.4.2 raises ClassNotFoundException: org.springframework.core.metrics.ApplicationStartup | 0.834 |  |  |
| 45 | [#26878](https://github.com/spring-projects/spring-boot/issues/26878) Spring-CLI: "packageName" parameter is not aligned with the docs and doesn't fit the naming convention of other parameters | [#7427](https://github.com/spring-projects/spring-boot/issues/7427) spring init --list shows packageName as a parameter | 0.834 |  |  |
| 46 | [#34645](https://github.com/spring-projects/spring-boot/issues/34645) Spring boot 3.0.4: New Test Application context is created for every new IT class  when using Kotlin Mock beans | [#22566](https://github.com/spring-projects/spring-boot/issues/22566) Spring Boot 2.3.2: new context is created for every IT class if SpyBean is used | 0.831 |  |  |
| 47 | [#4556](https://github.com/spring-projects/spring-boot/issues/4556) Adding @EnableHypermediaSupport prevents spring.jackson.* properties from working on _halObjectMapper | [#1949](https://github.com/spring-projects/spring-boot/issues/1949) Using Spring HATEOAS prevents spring.jackson.* configuration from taking effect | 0.829 |  |  |
| 48 | [#48052](https://github.com/spring-projects/spring-boot/issues/48052) Document how to use ContextPropagatingTaskDecorator for propagating trace context over thread boundaries | [#47893](https://github.com/spring-projects/spring-boot/issues/47893) Document how to use ContextPropagatingTaskDecorator for propagating trace context over thread boundaries | 0.827 | yes |  |
