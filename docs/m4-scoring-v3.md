# Draft replies, two prompt versions side by side (30 recent spring-projects/spring-boot issues)

The same 30 issues as docs/m4-scoring.md. For each issue there are two drafts, **A** and **B**, written by two versions
of the draft prompt; which is which changes from issue to issue, and the key is at the end. Score each draft
**useful / partly / wrong** before looking at the key.

New reports made with `claude-haiku-5-5` by `scripts/m4-scoring-v3.py`; 91,588 tokens in total ($0.009 to $0.046).

| # | Issue | Top match | Draft A | Draft B | Notes |
|---|---|---|---|---|---|
| 1 | [#52582](https://github.com/spring-projects/spring-boot/issues/52582) ClearCachesApplicationListener does not find inherited clear | [#15964](https://github.com/spring-projects/spring-boot/issues/15964) 66% |  |  |  |
| 2 | [#52581](https://github.com/spring-projects/spring-boot/issues/52581) Nested WritableJson.toByteArray on the same thread corrupts  | [#51154](https://github.com/spring-projects/spring-boot/issues/51154) 79% |  |  |  |
| 3 | [#52580](https://github.com/spring-projects/spring-boot/issues/52580) JsonWriter ignores a ValueProcessor implemented as a class | [#35891](https://github.com/spring-projects/spring-boot/issues/35891) 63% |  |  |  |
| 4 | [#52567](https://github.com/spring-projects/spring-boot/issues/52567) AcceptLanguage: More than the maximum (10) allowed number of | [#7930](https://github.com/spring-projects/spring-boot/issues/7930) 65% |  |  |  |
| 5 | [#52553](https://github.com/spring-projects/spring-boot/issues/52553) Binding a value from a config tree fails after a collection  | [#38734](https://github.com/spring-projects/spring-boot/issues/38734) 72% |  |  |  |
| 6 | [#52529](https://github.com/spring-projects/spring-boot/issues/52529) Support MockServer (https://www.mock-server.com) ServiceConn | [#52519](https://github.com/spring-projects/spring-boot/issues/52519) 81% |  |  |  |
| 7 | [#52521](https://github.com/spring-projects/spring-boot/issues/52521) Built-in support for P6spy | [#44010](https://github.com/spring-projects/spring-boot/issues/44010) 43% |  |  |  |
| 8 | [#52520](https://github.com/spring-projects/spring-boot/issues/52520) Built-in support for Ryuk-like process in spring-boot-docker | [#35021](https://github.com/spring-projects/spring-boot/issues/35021) 65% |  |  |  |
| 9 | [#52519](https://github.com/spring-projects/spring-boot/issues/52519) Support MockServerConnectionDetails on DockerComposeService | [#45652](https://github.com/spring-projects/spring-boot/issues/45652) 73% |  |  |  |
| 10 | [#52516](https://github.com/spring-projects/spring-boot/issues/52516) CacheAspectSupport initialisation does not support multiple  | [#37095](https://github.com/spring-projects/spring-boot/issues/37095) 74% |  |  |  |
| 11 | [#52515](https://github.com/spring-projects/spring-boot/issues/52515) Support Optional<NestedClass> in @ConfigurationProperties | [#15999](https://github.com/spring-projects/spring-boot/issues/15999) 80% |  |  |  |
| 12 | [#52514](https://github.com/spring-projects/spring-boot/issues/52514) spring-boot-starter-hazelcast does not include hazelcast-spr | [#19487](https://github.com/spring-projects/spring-boot/issues/19487) 81% |  |  |  |
| 13 | [#52505](https://github.com/spring-projects/spring-boot/issues/52505) Consider publishing a JSON schema for Configuration Metadata | [#40315](https://github.com/spring-projects/spring-boot/issues/40315) 76% |  |  |  |
| 14 | [#52500](https://github.com/spring-projects/spring-boot/issues/52500)  JSON parse failures no longer produce DockerOutputParseExce | [#52499](https://github.com/spring-projects/spring-boot/issues/52499) 98% |  |  |  |
| 15 | [#52499](https://github.com/spring-projects/spring-boot/issues/52499)  JSON parse failures no longer produce DockerOutputParseExce | [#38220](https://github.com/spring-projects/spring-boot/issues/38220) 75% |  |  |  |
| 16 | [#52498](https://github.com/spring-projects/spring-boot/issues/52498) Register ObservationAwareBaggageThreadLocalAccessor so that  | [#35906](https://github.com/spring-projects/spring-boot/issues/35906) 66% |  |  |  |
| 17 | [#52486](https://github.com/spring-projects/spring-boot/issues/52486) Provide customizer callback for embedded LDAP's InMemoryDire | [#51437](https://github.com/spring-projects/spring-boot/issues/51437) 76% |  |  |  |
| 18 | [#52465](https://github.com/spring-projects/spring-boot/issues/52465) Caffeine cache auto-configuration ignores an AsyncCacheLoade | [#51843](https://github.com/spring-projects/spring-boot/issues/51843) 85% |  |  |  |
| 19 | [#52401](https://github.com/spring-projects/spring-boot/issues/52401) Locale parsing regression in Spring Boot 4.1.2: Accept-Langu | [#6990](https://github.com/spring-projects/spring-boot/issues/6990) 63% |  |  |  |
| 20 | [#52400](https://github.com/spring-projects/spring-boot/issues/52400) [Security] Bump logback-classic to >= 1.6.3 due to CVE-2026- | [#38643](https://github.com/spring-projects/spring-boot/issues/38643) 79% |  |  |  |
| 21 | [#52394](https://github.com/spring-projects/spring-boot/issues/52394) Introspection setting silently ignored when a custom GraphQl | [#33096](https://github.com/spring-projects/spring-boot/issues/33096) 73% |  |  |  |
| 22 | [#52392](https://github.com/spring-projects/spring-boot/issues/52392) @RestController returning a POJO fails with NoClassDefFoundE | [#1190](https://github.com/spring-projects/spring-boot/issues/1190) 80% |  |  |  |
| 23 | [#52390](https://github.com/spring-projects/spring-boot/issues/52390) Native image startup fails with Redisson on Spring Boot 4.1. | [#35587](https://github.com/spring-projects/spring-boot/issues/35587) 69% |  |  |  |
| 24 | [#52379](https://github.com/spring-projects/spring-boot/issues/52379) OGNL should excluded when using thymeleaf-layout-dialect | [#6862](https://github.com/spring-projects/spring-boot/issues/6862) 72% |  |  |  |
| 25 | [#52366](https://github.com/spring-projects/spring-boot/issues/52366) GitHub Release notes missing for v4.1.2 | [#27692](https://github.com/spring-projects/spring-boot/issues/27692) 79% |  |  |  |
| 26 | [#52347](https://github.com/spring-projects/spring-boot/issues/52347) configprops endpoint prefix selector also returns beans of s | [#14880](https://github.com/spring-projects/spring-boot/issues/14880) 69% |  |  |  |
| 27 | [#52320](https://github.com/spring-projects/spring-boot/issues/52320) Spring Batch JDBC auto-configuration connects to the databas | [#49368](https://github.com/spring-projects/spring-boot/issues/49368) 69% |  |  |  |
| 28 | [#52316](https://github.com/spring-projects/spring-boot/issues/52316) X-Forwarded-Prefix can no longer be enabled with the NATIVE  | [#10900](https://github.com/spring-projects/spring-boot/issues/10900) 69% |  |  |  |
| 29 | [#52302](https://github.com/spring-projects/spring-boot/issues/52302) OnAvailableEndpointCondition caches retain closed applicatio | [#50954](https://github.com/spring-projects/spring-boot/issues/50954) 67% |  |  |  |
| 30 | [#52301](https://github.com/spring-projects/spring-boot/issues/52301) Maven process-test-aot only copies META-INF/native-image of  | [#47837](https://github.com/spring-projects/spring-boot/issues/47837) 74% |  |  |  |

## 1. [#52582](https://github.com/spring-projects/spring-boot/issues/52582) ClearCachesApplicationListener does not find inherited clearCache() methods

Top match: [#15964](https://github.com/spring-projects/spring-boot/issues/15964) org.springframework.boot.loader.jar.Handler rootFileCache can not be refreshed  (65.8%)

**Draft A (ok):**

> Thanks for the detailed report and the class hierarchy snippet. The mechanism you describe (getDeclaredMethod not seeing the inherited clearCache() on LaunchedClassLoader) is plausible and matches the code, so a maintainer will look into it.
>
> To confirm the failure end to end, could you share the logs or the stack trace from a run where the context refreshes in a launched executable jar? A breakpoint or log output inside clearClassLoaderCaches showing the NoSuchMethodException being swallowed would also help.

**Draft B (ok):**

> Thanks for the detailed report. The reflective lookup analysis looks plausible, and the `getMethod` vs `getDeclaredMethod` distinction is exactly the kind of thing that fails silently. The Spring Boot caching docs (https://docs.spring.io/spring-boot/4.1/reference/io/caching.html#io.caching.provider) don't cover `ClearCachesApplicationListener` or `JarUrlClassLoader`, so I can't confirm the intended behaviour from them.
>
> Could you share a minimal reproduction, or the stack trace or breakpoint output from the live refresh you mentioned? That would let us confirm the caches are retained in practice. Thanks for pointing at commit 7ad4a9817d as the likely origin.

## 2. [#52581](https://github.com/spring-projects/spring-boot/issues/52581) Nested WritableJson.toByteArray on the same thread corrupts both results

Top match: [#51154](https://github.com/spring-projects/spring-boot/issues/51154) Structured logging: a failed JSON encode corrupts the next log event written on the same thread (78.5%)

**Draft A (ok):**

> Thanks for the detailed report and minimal reproduction. The nested `toByteArray` collision on the per-thread `AppendableByteArray` cache is a clear explanation of the corrupted output you're seeing.
>
> This looks related to issue #51154, which describes a failed JSON encode corrupting the next log event written on the same thread. It may be the same root cause or a close cousin, so it would be worth comparing the two. The structured logging docs are at https://docs.spring.io/spring-boot/4.1/reference/features/logging.html#features.logging.structured, though they don't cover this internal buffer behaviour.
>
> We haven't reproduced this yet, so we can't confirm the fix you proposed. Your mention of differing console and file level thresholds is useful and we'll keep it in mind.

**Draft B (ok):**

> This looks like #51154, which was closed as not planned. That issue covers a related corruption where a failed JSON encode leaks into the next event on the same thread, but your report is a different trigger: a nested `WritableJson.toByteArray` call sharing the per-thread `AppendableByteArray` cache. The shared mechanism is worth checking against your analysis.
>
> Thanks for the detailed reproduction and the clear explanation of the cache collision. To help us triage, could you share the logs or stack trace from the console and file output for the outer event, plus the `toString()` implementation you're using in the key-value value? Seeing the actual corrupted output would help confirm this matches your minimal case.

## 3. [#52580](https://github.com/spring-projects/spring-boot/issues/52580) JsonWriter ignores a ValueProcessor implemented as a class

Top match: [#35891](https://github.com/spring-projects/spring-boot/issues/35891) Jackson's ObjectMapper writes empty JSON strings for Spring Boot 3.1.0 if the entity is a record (62.8%)

**Draft A (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

**Draft B (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

## 4. [#52567](https://github.com/spring-projects/spring-boot/issues/52567) AcceptLanguage: More than the maximum (10) allowed number of locales

Top match: [#7930](https://github.com/spring-projects/spring-boot/issues/7930) Allow supported locales to be configured via the environment (64.5%)

**Draft A (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

**Draft B (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

## 5. [#52553](https://github.com/spring-projects/spring-boot/issues/52553) Binding a value from a config tree fails after a collection of the same element type has been bound from the same config tree

Top match: [#38734](https://github.com/spring-projects/spring-boot/issues/38734) Custom binding converters are ignored when working with collection types (72.2%)

**Draft A (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

**Draft B (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

## 6. [#52529](https://github.com/spring-projects/spring-boot/issues/52529) Support MockServer (https://www.mock-server.com) ServiceConnection

Top match: [#52519](https://github.com/spring-projects/spring-boot/issues/52519) Support MockServerConnectionDetails on DockerComposeService (80.7%)

**Draft A (ok):**

> Thanks for the suggestion. The sources I have cover Testcontainers service connections and Docker Compose support in general, but they don't document how MockServer would be detected or wired up, so I can't confirm whether it's already supported or what the right approach would be. The Testcontainers service connection docs list which container types get built-in connection details: https://docs.spring.io/spring-boot/4.1/reference/testing/testcontainers.html#testing.testcontainers.service-connections
>
> It looks like you already have a tentative implementation in #52519, which would be the natural place to discuss the details. Could you share the Spring Boot version you're using and the Docker Compose service definition you'd expect to be detected? That would help the maintainers evaluate the request.

**Draft B (ok):**

> This looks like #52519, which was closed as not planned. That issue asked for the same thing: MockServerConnectionDetails support for DockerComposeService, and this request is essentially the same, so the decision to decline likely still applies.
>
> Since the maintainers did not accept that proposal, a new issue would need a different angle to be reconsidered, such as an argument for why this belongs in Spring Boot rather than in a third-party module. Otherwise, the Docker Compose service connection support in Spring Boot is documented at https://docs.spring.io/spring-boot/4.1/how-to/docker-compose.html#howto.docker-compose and may already let you wire MockServer up with a custom connection details bean.
>
> Could you say whether you tried that route, and what a custom ConnectionDetails for MockServer would need to provide that the docs don't cover?

## 7. [#52521](https://github.com/spring-projects/spring-boot/issues/52521) Built-in support for P6spy

Top match: [#44010](https://github.com/spring-projects/spring-boot/issues/44010) Upgrade to Jboss Transaction Spi 7.6.1.Final (42.9%)

**Draft A (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

**Draft B (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

## 8. [#52520](https://github.com/spring-projects/spring-boot/issues/52520) Built-in support for Ryuk-like process in spring-boot-docker-compose

Top match: [#35021](https://github.com/spring-projects/spring-boot/issues/35021) Support testcontainers lifecycle methods when used as Spring beans (65.2%)

**Draft A (ok):**

> Thanks for the suggestion and for the detailed reasoning. The Spring Boot reference on Testcontainers describes how containers are started and stopped with the application context: containers managed by Spring are shut down as part of the TestContext Framework's standard shutdown, and Testcontainers-managed containers have no ordering guarantee relative to beans (https://docs.spring.io/spring-boot/4.1/reference/testing/testcontainers.html#testing.testcontainers.lifecycle). Those docs don't cover cleanup after a SIGKILL or machine interruption, nor whether a Ryuk-like mechanism is planned for spring-boot-docker-compose, so I can't speak to that. Could you share your Spring Boot version, your Docker Compose setup, and the steps to reproduce the leftover containers? That would help the maintainers evaluate the request.

**Draft B (ok):**

> Thanks for the suggestion. The Testcontainers docs on lifecycle of managed containers (https://docs.spring.io/spring-boot/4.1/reference/testing/testcontainers.html#testing.testcontainers.lifecycle) describe how Testcontainers-managed containers are stopped during context shutdown, and note that shutdown ordering isn't guaranteed. That doc doesn't cover cleanup after a SIGKILL or machine crash, so it doesn't settle your question.
>
> The spring-boot-docker-compose support is designed around the lifecycle of a running application, so a Ryuk-style reaper is a fair request to evaluate. We'd want to think through how it fits with Compose project naming and with containers that users intentionally keep running between runs. We haven't made a decision on it. If you have a concrete use case, such as which crash scenarios matter most, please describe it here.

## 9. [#52519](https://github.com/spring-projects/spring-boot/issues/52519) Support MockServerConnectionDetails on DockerComposeService

Top match: [#45652](https://github.com/spring-projects/spring-boot/issues/45652) Make integration testing of webclient easier with @MockServerResponse annotation. (73.0%)

**Draft A (ok):**

> Thanks for the detailed write-up. Your factory approach is a reasonable workaround for a service that Spring Boot doesn't yet ship a connection-details type for. A custom DockerComposeConnectionDetailsFactory like this is the supported extension point, so you're not doing anything unusual.
>
> Could you tell us which Spring Boot version you're using? That will help us check how your code fits with the current DockerCompose support.

**Draft B (ok):**

> Thanks for the detailed write-up. The sources I have cover Spring Boot's Testcontainers service connections and mock environment testing, not Docker Compose connection details, so I can't confirm whether built-in MockServer support is planned or how it should be designed.
>
> The closest relevant material is the Testcontainers service connections section, which lists the connection detail types provided out of the box and how factories are registered: https://docs.spring.io/spring-boot/4.1/reference/testing/testcontainers.html#testing.testcontainers.service-connections
>
> Could you tell us which Spring Boot version you're on and whether your factory works as expected? That would help the team evaluate the request.

## 10. [#52516](https://github.com/spring-projects/spring-boot/issues/52516) CacheAspectSupport initialisation does not support multiple CacheManager

Top match: [#37095](https://github.com/spring-projects/spring-boot/issues/37095) CacheMetricsRegistrarConfiguration fails to start if there is a request scoped cache manager in the context (74.0%)

**Draft A (ok):**

> Thanks for the report. The Spring Boot docs (https://docs.spring.io/spring-boot/4.1/reference/io/caching.html#io.caching.provider) say that Boot only auto-configures a CacheManager when you have not defined your own CacheManager or a CacheResolver bean named `cacheResolver`, and that you can customize the auto-configured manager through CacheManagerCustomizer. The docs don't cover how the `cacheManager` attribute on `@Cacheable` interacts with multiple CacheManager beans, so I can't confirm whether the startup failure you describe is expected behaviour or a bug. Could you share a minimal reproducer, your full stack trace, and whether the same setup works on 4.1.x? That would help the maintainers look into it.

**Draft B (ok):**

> Thanks for the report. Could you share the steps to reproduce this, ideally a minimal sample with the two CacheManager beans and the @Cacheable usage? Please also include the full logs or stack trace from the startup failure, so we can see exactly where it breaks in afterSingletonsInstantiated().
>
> With that we can check whether the cacheManager attribute is being honored when Spring Boot auto-configures the cache resolver, and whether a default resolver should handle this case.

## 11. [#52515](https://github.com/spring-projects/spring-boot/issues/52515) Support Optional<NestedClass> in @ConfigurationProperties

Top match: [#15999](https://github.com/spring-projects/spring-boot/issues/15999) Optional nested configuration property not assigned (80.4%)

**Draft A (ok):**

> Thanks for the report. The docs say that an Optional constructor-bound property with no value is bound as an empty Optional (https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.constructor-binding), and that default values are applied to missing properties via @DefaultValue (https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.constructor-binding.default-values). Related earlier discussions include https://github.com/spring-projects/spring-boot/issues/15999 and https://github.com/spring-projects/spring-boot/issues/19803.
>
> I can't tell from these sources whether Optional<NestedClass> is supported in this case, so this may be a limitation or a bug. To help us investigate, could you share a minimal reproducer, including your record definition and the exact binding result you get on 4.2.0-M2? Please also confirm whether the nested block is absent or present but empty in the YAML.

**Draft B (ok):**

> This looks like #15999, which was closed as completed (fixed), and it covers optional nested properties. The fix may already handle this case, so it's worth checking whether your 4.2.0-M2 setup is affected by it.
>
> To help us reproduce this, could you share the steps to reproduce? Specifically, the exact record and nested class definitions, the full YAML you're binding, and whether you're binding with @ConfigurationProperties on a record or using constructor binding with a separate @EnableConfigurationProperties or @ConfigurationPropertiesScan.
>
> Relevant reference: https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.constructor-binding

## 12. [#52514](https://github.com/spring-projects/spring-boot/issues/52514) spring-boot-starter-hazelcast does not include hazelcast-spring dependency

Top match: [#19487](https://github.com/spring-projects/spring-boot/issues/19487) Create Hazelcast config with SpringManagedContext when available (81.5%)

**Draft A (ok):**

> Thanks for the report. The Hazelcast docs describe the caching integration: Spring Boot wraps an auto-configured HazelcastInstance in a CacheManager only when `com.hazelcast:hazelcast-spring` is on the classpath (see https://docs.spring.io/spring-boot/4.1/reference/io/caching.html#io.caching.provider.hazelcast). Setting `spring.cache.type` to `hazelcast` selects that CacheManager-based implementation.
>
> This doesn't directly address whether the starter should bring in that dependency, so the maintainers may need to check the starter's intended contents. To help reproduce this, could you share the output of your dependency tree (for example, `mvn dependency:tree` or `gradle dependencies`), your `spring.cache.type` setting, and the full startup log around the cache configuration?

**Draft B (ok):**

> This looks like #19487, which was closed as completed (fixed), so the fix may already cover part of this. Please check whether that fix applies to your setup. Your report also describes a dependency problem, which is a different area, so it may need its own discussion.
>
> Could you share the steps to reproduce this, including the build file and how you add the starter? Please also include the logs or stack trace from startup, especially any message about the missing HazelcastCacheManager class.
>
> The Hazelcast docs for caching are here: https://docs.spring.io/spring-boot/4.1/reference/io/caching.html#io.caching.provider.hazelcast

## 13. [#52505](https://github.com/spring-projects/spring-boot/issues/52505) Consider publishing a JSON schema for Configuration Metadata

Top match: [#40315](https://github.com/spring-projects/spring-boot/issues/40315) Provide an alternative to javadoc for documenting configuration properties (75.5%)

**Draft A (ok):**

> This looks like #40315, which was closed as not planned. That issue asked for an alternative to javadoc for documenting configuration properties, which overlaps with the JUnit use case here, so the earlier decision is relevant context.
>
> This request is narrower, though: a JSON Schema for the metadata format itself, so hand-written manual hints can be validated and get IDE support. The existing reference pages linked above describe the format informally, so a schema would be a useful addition to them.
>
> Could you say which Spring Boot version you're targeting and whether you have a draft schema or example hints file you'd like us to check against?

**Draft B (ok):**

> Thanks for the detailed proposal. The sources I have cover how Spring Boot binds configuration properties and how @ConfigurationProperties validation and metadata work, but they don't describe the Configuration Metadata file format itself or any JSON Schema for it, so I can't confirm the current state of that format from them. The linked Metadata Format and Providing Manual Hints pages are the right place to check what's documented today.
>
> For background on how metadata is produced and consumed, see https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.using-annotated-types, which notes that @ConfigurationProperties types enable IDE auto-completion via generated metadata. Related discussion of documenting configuration properties is in https://github.com/spring-projects/spring-boot/issues/40315.
>
> Could you share a sample manual hints document and the validation errors you'd like to catch? That would help us judge what a schema needs to cover.

## 14. [#52500](https://github.com/spring-projects/spring-boot/issues/52500)  JSON parse failures no longer produce DockerOutputParseExceptions

Top match: [#52499](https://github.com/spring-projects/spring-boot/issues/52499)  JSON parse failures no longer produce DockerOutputParseExceptions (98.4%)

**Draft A (ok):**

> Thanks for opening this. It looks like a forward port of #52499 to 4.2.x, which is already tracked at https://github.com/spring-projects/spring-boot/issues/52499. The related report at https://github.com/spring-projects/spring-boot/issues/38220 covers Docker JSON parsing failures on certain locales, which may be relevant context.
>
> I can't confirm from these sources what the 4.2.x status or timeline is, so a maintainer will need to weigh in on whether this should proceed as a separate backport. Thanks for your patience.

**Draft B (ok):**

> This looks like #52499, which was closed as completed (fixed). This new issue is the forward port of that same change to 4.2.x, so it tracks the same behavior. Since the original was fixed, please check whether that fix already covers your case, or whether the 4.2.x branch still shows the problem.
>
> If it still happens, could you share the Spring Boot version you're running, the steps to reproduce, what you expected versus what you saw, and any logs or stack trace?

## 15. [#52499](https://github.com/spring-projects/spring-boot/issues/52499)  JSON parse failures no longer produce DockerOutputParseExceptions

Top match: [#38220](https://github.com/spring-projects/spring-boot/issues/38220) Docker JSON parsing fails on certain locales (75.2%)

**Draft A (ok):**

> This looks like #38220, which was closed as completed (fixed). That one covered Docker JSON parsing failing under certain locales, which may be related to what you're seeing here. Could you check whether the fix in #38220 covers your case, and if not, let us know?
>
> To help us look into this, please share:
> - The Spring Boot version you're running
> - Steps to reproduce
> - Expected vs. actual behavior
> - Logs or the stack trace
>
> Thanks!

**Draft B (ok):**

> Thanks for the report! This looks related to #38220, which covers Docker JSON parsing failing under certain locales (https://github.com/spring-projects/spring-boot/issues/38220). However, the new issue refers to #52380, which isn't among the sources I can check, so I can't confirm how the two relate or what changed in 4.1.x.
>
> To help us triage, could you share the 4.1.x version you're on, the exception or log output you're seeing, and a short description of what behavior changed compared with the earlier release? If the forward-port reason is clear from #52380, a link to it would also help.

## 16. [#52498](https://github.com/spring-projects/spring-boot/issues/52498) Register ObservationAwareBaggageThreadLocalAccessor so that baggage created with the Tracer API survives thread hops

Top match: [#35906](https://github.com/spring-projects/spring-boot/issues/35906) Automatically register the ObservationAwareSpanThreadLocalAccessor after ObservationThreadLocalAccessor (66.1%)

**Draft A (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

**Draft B (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

## 17. [#52486](https://github.com/spring-projects/spring-boot/issues/52486) Provide customizer callback for embedded LDAP's InMemoryDirectoryServerConfig

Top match: [#51437](https://github.com/spring-projects/spring-boot/issues/51437) Add property to configure required authentication operation types for embedded LDAP (75.8%)

**Draft A (ok):**

> This looks like #51437, which was closed as not planned. That issue asked for a property to configure required authentication operation types for the embedded LDAP server, so it is related but addresses a different need. Since it was closed without a fix, the interceptor request would need its own discussion.
>
> Could you share a minimal example of the UPN-to-DN mapping setup you're trying to test, and whether a custom `InMemoryDirectoryServerConfig` bean in a test configuration works for you as a workaround? Knowing whether that workaround fails and how would help us judge whether either option is needed.

**Draft B (ok):**

> Thanks for the detailed proposal, and for the kind words about #51437.
>
> The current docs cover the embedded LDAP server: adding `com.unboundid:unboundid-ldapsdk`, setting `spring.ldap.embedded.base-dn`, and optionally loading initial data via `schema.ldif` or `spring.ldap.embedded.ldif`. See https://docs.spring.io/spring-boot/4.1/reference/data/nosql.html#data.nosql.ldap.embedded
>
> The sources I have don't describe any hook for registering custom `InMemoryOperationInterceptor` instances, so I can't confirm whether either option is already supported. Could you let us know which Spring Boot version you're on and whether you've tried any approach beyond the documented properties? Your Option 1 and Option 2 sketches would be useful to discuss with the maintainers.

## 18. [#52465](https://github.com/spring-projects/spring-boot/issues/52465) Caffeine cache auto-configuration ignores an AsyncCacheLoader bean in 4.2

Top match: [#51843](https://github.com/spring-projects/spring-boot/issues/51843) Expose Caffeine async cache mode via a configuration property (84.6%)

**Draft A (ok):**

> Thanks for the report and the clear reproduction. Based on the Spring Boot reference documentation, Caffeine customization is driven by `spring.cache.caffeine.spec`, a `CaffeineSpec` bean, or a `Caffeine` bean, and a `CacheLoader<Object, Object>` bean is associated with the cache manager automatically; other generic types are ignored (https://docs.spring.io/spring-boot/4.1/reference/io/caching.html#io.caching.provider.caffeine). The docs don't describe an `AsyncCacheLoader` bean being picked up, so this looks like a gap in the auto-configuration rather than documented behavior. Related async-mode discussion is in https://github.com/spring-projects/spring-boot/issues/51843. A PR would be welcome; please let us know if you'd like guidance on where the change should go.

**Draft B (ok):**

> This looks like #51843, which was closed as not planned. That issue asked for the same async cache mode to be exposed through a property, which is the groundwork this report builds on, so the closure may be relevant to whether the loader support gets picked up. The reporter could check the discussion there before opening a PR.
>
> Could you share the logs or stack trace from the 4.2.0-RC1 run, specifically anything logged when the cache manager is created and when `get` returns null? That would help confirm whether the bean is being skipped during auto-configuration.

## 19. [#52401](https://github.com/spring-projects/spring-boot/issues/52401) Locale parsing regression in Spring Boot 4.1.2: Accept-Language: zh-Hans-CN;q=1.0 produces wrong toLanguageTag() output

Top match: [#6990](https://github.com/spring-projects/spring-boot/issues/6990) spring.mvc.locale is ignored (63.4%)

**Draft A (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

**Draft B (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

## 20. [#52400](https://github.com/spring-projects/spring-boot/issues/52400) [Security] Bump logback-classic to >= 1.6.3 due to CVE-2026-19880 (Path Traversal)

Top match: [#38643](https://github.com/spring-projects/spring-boot/issues/38643) CVE for logback 1.4.11 with the latest SpringBoot 3.2 (79.0%)

**Draft A (ok):**

> Thanks for the report. Our sources cover earlier, related Logback CVE discussions but do not address CVE-2026-19880 or the 1.6.3 version you mention, so I can't confirm the fix version or its status from them. Related prior threads are https://github.com/spring-projects/spring-boot/issues/38643 and https://github.com/spring-projects/spring-boot/issues/38926, which may give context on how Logback upgrades have been handled. To help us triage, could you share the NVD or advisory link for CVE-2026-19880 and confirm the exact Spring Boot version you are using (the 4.1.2 path in your report)? Thank you for the detailed description.

**Draft B (ok):**

> This looks like #38643, which was closed as not planned. That issue was about an earlier logback CVE, so it's related but not the same vulnerability. Its closure means the maintainers have decided against some logback bumps before, so we should be careful not to promise anything here.
>
> To help us triage this, could you share the expected versus actual result of the scan, meaning what the scanner reports and which version it flags, plus the relevant logs or stack trace from the build pipeline? That will confirm the resolved version and the dependency path you listed.

## 21. [#52394](https://github.com/spring-projects/spring-boot/issues/52394) Introspection setting silently ignored when a custom GraphQlSource bean is present

Top match: [#33096](https://github.com/spring-projects/spring-boot/issues/33096) GraphQL auto-configuration should not back off when existing GraphQlSource bean (73.0%)

**Draft A (ok):**

> Thanks for the detailed report and the reproduction test. We can see the reasoning: introspection is only disabled inside the `@ConditionalOnMissingBean` method, so a custom `GraphQlSource` skips it.
>
> On your second point, we agree that a silent fail-open setting is worse than a loud failure. Before we decide between moving the setting out of the conditional method and logging a warning, we'd like to see the logs or stack trace from a run that reproduces this, so we can confirm nothing else is interfering.
>
> Please share the logs or stack trace from your reproduction, including the startup output and whether any warning appears.

**Draft B (ok):**

> Thanks for the detailed report and the reproduction test. The behaviour you describe matches how the autoconfigured `graphQlSource` method backs off when a custom `GraphQlSource` bean is present, so the introspection setting is never applied in that case. The docs on schema introspection are at https://docs.spring.io/spring-boot/4.1/reference/web/spring-graphql.html#web.graphql.schema, and they describe the property without mentioning this interaction.
>
> Your two proposed resolutions look like good starting points for discussion. A maintainer will take a closer look at whether the setting should apply regardless of who creates the `GraphQlSource`, or whether a startup warning is the better fit.
>
> Thanks again for the clear write-up.

## 22. [#52392](https://github.com/spring-projects/spring-boot/issues/52392) @RestController returning a POJO fails with NoClassDefFoundError: com/fasterxml/jackson/annotation/JsonApplyView on Spring Boot 4.1.2 / Jackson 3.2.2

Top match: [#1190](https://github.com/spring-projects/spring-boot/issues/1190) Broken Jackson getter serialization. (79.8%)

**Draft A (ok):**

> Thanks for the detailed report. The sources I have cover how Spring Boot 4.1 handles JSON responses and Jackson configuration, but they don't address this specific class-loading failure or the version mismatch between jackson-databind and jackson-annotations, so I can't confirm the cause from them.
>
> Useful context for the maintainers: the default JSON setup is described at https://docs.spring.io/spring-boot/4.1/how-to/spring-mvc.html#howto.spring-mvc.write-json-rest-service, and the isolated Actuator JsonMapper is at https://docs.spring.io/spring-boot/4.1/reference/actuator/endpoints.html#actuator.endpoints.json.
>
> Could you run `mvn dependency:tree -Dincludes=com.fasterxml.jackson.core` and confirm whether any other Jackson 2.x artifact appears on the classpath? Also, does the error reproduce with a minimal project and no other dependencies?

**Draft B (ok):**

> This looks like #1190, which was closed as completed. That one is about Jackson getter serialization, so it's only loosely related: the failure here comes from a jackson-databind 3.2.2 and jackson-annotations 2.21 mismatch in the Spring Boot 4.1.2 dependency tree, not from getters. Since it was closed as fixed, it may be worth checking whether the fix covers this dependency combination.
>
> Thanks for the detailed reproduction and dependency tree. Could you check whether a newer Spring Boot 4.1.x release resolves this? If it still reproduces there, it would help to know whether the BOM-managed jackson-annotations version is being overridden anywhere in your build.

## 23. [#52390](https://github.com/spring-projects/spring-boot/issues/52390) Native image startup fails with Redisson on Spring Boot 4.1.2: io.netty.util.ReferenceCountUtil cannot initialize at run time

Top match: [#35587](https://github.com/spring-projects/spring-boot/issues/35587) Spring boot3.0  using Redisson can not startup when AOT compile (69.4%)

**Draft A (ok):**

> Thanks for the detailed report and reproduction steps. The sources I have cover building and testing native images with Maven and the general known-limitations guidance, but they don't address this specific startup failure or the difference between Spring Boot 4.1.1 and 4.1.2, so I can't offer a diagnosis from them.
>
> Useful next steps are the Spring Boot with GraalVM wiki page, https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-with-GraalVM, and the spring-aot-smoke-tests project. If the problem is in a third-party library's native metadata, the reachability metadata project is the right place to raise it.
>
> Could you share the full stack trace, including the original `ExceptionInInitializerError` if you can capture it (for example with `-H:+ReportExceptionStackTraces` at image build time), and confirm whether the minimal reproduction in jfoundry fails on a clean checkout? That would help the maintainers narrow this down.

**Draft B (ok):**

> Thanks for the detailed report and the reproduction steps. There's no earlier issue here that covers this, so I'll leave it to the team to triage.
>
> The key detail is that the same Netty version fails on 4.1.2 and passes on 4.1.1, which points at something in Spring Boot's native build output rather than Netty alone. That's useful. If you can, please attach the output of your native build with `-H:+PrintClassInitialization` for `io.netty.util.ReferenceCountUtil`, so we can see where it gets initialized at build time.
>
> Also, the Spring Boot wiki page on GraalVM (linked from our native image docs) and the reachability metadata project are good places to check whether Redisson or Netty need hints you may not be providing.

## 24. [#52379](https://github.com/spring-projects/spring-boot/issues/52379) OGNL should excluded when using thymeleaf-layout-dialect

Top match: [#6862](https://github.com/spring-projects/spring-boot/issues/6862) Add Thymeleaf with dialect (71.6%)

**Draft A (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

**Draft B (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

## 25. [#52366](https://github.com/spring-projects/spring-boot/issues/52366) GitHub Release notes missing for v4.1.2

Top match: [#27692](https://github.com/spring-projects/spring-boot/issues/27692) Spring Boot 2.2 Release Notes missing from Wiki (79.0%)

**Draft A (ok):**

> This looks like #27692, which was closed as completed. That issue was about the 2.2 release notes being missing from the wiki, and the fix there may not cover GitHub release pages for newer versions like 4.1.2, so it's worth checking whether this is the same gap. Release notes for Spring Boot are generally published on the project wiki, and the upgrade guide links to them from https://docs.spring.io/spring-boot/4.1/upgrading.html#upgrading.from-2x. The 4.1.2 GitHub release page itself may simply not have a description yet. Could you confirm whether the release notes appear on the wiki's Release Notes page for 4.1.2? That would help us tell whether this is a missing wiki entry or a missing GitHub release description.

**Draft B (ok):**

> Thanks for raising this. The release notes for Spring Boot have historically lived on the project wiki rather than on the GitHub release page, so the empty v4.1.2 release page may be expected. The Spring Boot reference documentation also points to the wiki for the "new and noteworthy" summaries: see https://docs.spring.io/spring-boot/4.1/upgrading.html#upgrading.from-2x and the release notes section linked from there.
>
> Could you confirm whether the wiki release notes for 4.1.2 are available? If they are not, that would be useful to know. Thanks for your help with the project.

## 26. [#52347](https://github.com/spring-projects/spring-boot/issues/52347) configprops endpoint prefix selector also returns beans of sibling prefixes (spring.data matches spring.datasource)

Top match: [#14880](https://github.com/spring-projects/spring-boot/issues/14880) Provide end-to-end tracability for bound @ConfigurationProperties (68.7%)

**Draft A (ok):**

> Thanks for the detailed report. The sibling-prefix matching you describe does look like a real filtering bug, and your suggested fix (matching on the dotted prefix boundary) reads sensibly. The sources I have here are general Actuator and configuration docs, so they don't cover the endpoint's filter implementation, and I can't confirm from them how the selector is meant to behave. A maintainer will need to check the code path you cited. If you have the regression test and change ready against 3.5.x, opening a pull request would be a helpful next step so it can be reviewed directly.

**Draft B (ok):**

> Thanks for the report. The `configprops` endpoint is documented in https://docs.spring.io/spring-boot/4.1/reference/actuator/endpoints.html#actuator.endpoints, and the sources I have don't describe how the prefix selector matches, so I can't confirm the `startsWith` behavior from them. A reproduction with the exact application setup and the `/actuator/configprops/spring.data` output would help the maintainers check it. Thanks also for the offer of a patch with a regression test against `3.5.x`; the maintainers can say whether they'd like it submitted as a pull request.

## 27. [#52320](https://github.com/spring-projects/spring-boot/issues/52320) Spring Batch JDBC auto-configuration connects to the database on startup to detect its type, with no way to configure it

Top match: [#49368](https://github.com/spring-projects/spring-boot/issues/49368) Spring batch auto-config allows the JobRegistry to be used as a Spring Bean (69.4%)

**Draft A (ok):**

> Thanks for the detailed report. Here's what the current docs say that's relevant: Spring Batch's JDBC schema initialization can be configured with spring.batch.jdbc.initialize-schema, and Spring Boot automatically detects beans that depend on database initialization (https://docs.spring.io/spring-boot/4.1/how-to/data-initialization.html#howto.data-initialization.batch). Spring Batch's own documentation describes the @EnableJdbcJobRepository and DefaultBatchConfiguration route for taking full control of its configuration (https://docs.spring.io/spring-boot/4.1/reference/io/spring-batch.html#io.spring-batch).
>
> However, I don't see anything in these sources that addresses detecting the database type at startup or supplying it through a property, so they don't settle whether a spring.batch.jdbc.database-type property is the right approach. Could you confirm which Spring Boot version you're on and whether a database-type property has been discussed elsewhere? That would help the maintainers evaluate the proposal.

**Draft B (ok):**

> Thanks for the detailed report. The connection happens because the inherited getDatabaseType() calls DatabaseType.fromMetaData(getDataSource()), and Boot doesn't currently expose a way to set the type. Your proposal of a spring.batch.jdbc.database-type property that bypasses detection when set makes sense, and it matches what Spring Batch already allows through @EnableJdbcJobRepository(databaseType = ...).
>
> Since no similar earlier issue turned up, this looks new. Spring Batch's initialization settings are in the Spring Batch section of the docs (https://docs.spring.io/spring-boot/4.1/reference/io/spring-batch.html#io.spring-batch) if anything else in that area is relevant. The related Spring Modulith report is spring-projects/spring-modulith#924.
>
> Thanks for flagging the AOT training-run interaction too.

## 28. [#52316](https://github.com/spring-projects/spring-boot/issues/52316) X-Forwarded-Prefix can no longer be enabled with the NATIVE strategy on Reactor Netty

Top match: [#10900](https://github.com/spring-projects/spring-boot/issues/10900) Reactor Netty does not support X-Forwarded-* request headers (69.1%)

**Draft A (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

**Draft B (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

## 29. [#52302](https://github.com/spring-projects/spring-boot/issues/52302) OnAvailableEndpointCondition caches retain closed application contexts through their Environment

Top match: [#50954](https://github.com/spring-projects/spring-boot/issues/50954) Closed ApplicationContexts are retained after eviction from the test context cache, causing OutOfMemoryError in large @SpringBootTest suites (67.1%)

**Draft A (ok):**

> Thank you for the detailed report and the heap dump analysis.
>
> The sources I have are the Spring Boot 4.1 Actuator endpoints reference (https://docs.spring.io/spring-boot/4.1/reference/actuator/endpoints.html#actuator.endpoints). They describe which endpoints exist and what they expose, but they don't cover the internals of OnAvailableEndpointCondition, its static caches, or how Environment references are held, so they don't directly address this issue.
>
> To help triage, could you confirm whether the retention also reproduces on the 4.1.x branch with the reproducer as written, and whether the cut-off part of the test class contains any additional cleanup logic?

**Draft B (ok):**

> Thanks for the detailed report and the heap dump chain. This doesn't look like a duplicate of an earlier issue, so there's nothing to link.
>
> Could you tell us what you expected and what you actually saw? The reproducer and the heap chain show the retention clearly, but a short statement of the expected behaviour (closed contexts being collectable after close) against the observed behaviour (retained via the soft-reference cache until memory pressure) would help us confirm the intended outcome.
>
> Your analysis points at the static caches in OnAvailableEndpointCondition keyed by Environment. Once you've confirmed the expected vs actual behaviour, we can take a closer look at the cache lifecycle.

## 30. [#52301](https://github.com/spring-projects/spring-boot/issues/52301) Maven process-test-aot only copies META-INF/native-image of the generated resources so the Logback configuration is lost in native tests

Top match: [#47837](https://github.com/spring-projects/spring-boot/issues/47837) Caused by: java.nio.file.FileAlreadyExistsException: target/spring-aot/test/resources/META-INF/spring/logback-model (73.5%)

**Draft A (ok):**

> Thanks for the detailed report and the reproducer. The analysis of `ProcessTestAotMojo` copying only `META-INF/native-image` looks plausible given the linked lines, and the workaround is useful to others hitting this.
>
> To help us confirm the cause, could you share the logs or stack trace from the failing `mvn -PnativeTest test` run, particularly any Logback initialization output that shows which configuration was picked up?

**Draft B (ok):**

> Thanks for the detailed report and the reproducer. The issue describes `ProcessTestAotMojo` copying only `META-INF/native-image` from the generated resources, so `META-INF/spring/logback-model` never reaches the native test image. The contrast with `ProcessAotMojo` and the Gradle `aotTest` source set is useful context. The workaround you shared is helpful for anyone blocked in the meantime. Thanks for taking the time to write this up; a maintainer will look into it. Could you confirm which Native Build Tools version you're using, in case that matters?

---

<details><summary>Key (open after scoring)</summary>

1: A = v3; 2: A = v2; 3: A = v3; 4: A = v3; 5: A = v2; 6: A = v2; 7: A = v2; 8: A = v2; 9: A = v3; 10: A = v2; 11: A = v2; 12: A = v2; 13: A = v3; 14: A = v2; 15: A = v3; 16: A = v3; 17: A = v3; 18: A = v2; 19: A = v3; 20: A = v2; 21: A = v3; 22: A = v2; 23: A = v2; 24: A = v2; 25: A = v3; 26: A = v3; 27: A = v2; 28: A = v2; 29: A = v2; 30: A = v3

</details>
