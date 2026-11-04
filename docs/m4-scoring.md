# M4 STOP A: 30 recent spring-projects/spring-boot issues, scored by hand

Reports created with `claude-haiku-5-5` (prompts: missing-details-v1,draft-reply-v2) by `scripts/m4-scoring.py`. Issues: the 30 most recent by external reporters. Total LLM tokens: 88,554 ($0.009 to $0.044 at Haiku 5.5's $0.10 input / $0.50 output per million; mostly input).

**How to score:** for each issue, open the link, read the report section below, and put **useful**, **partly** or **wrong** in the Score column (and a few words in Notes if not useful). Judge it as a maintainer: would this report have saved you time?

| # | Issue | Top match (score, LLM check) | Labels | Missing | Draft | Score | Notes |
|---|---|---|---|---|---|---|---|
| 1 | [#52582](https://github.com/spring-projects/spring-boot/issues/52582) ClearCachesApplicationListener does not find inherited clear | [#15964](https://github.com/spring-projects/spring-boot/issues/15964) 0.66 | - | nothing | ok |  |  |
| 2 | [#52581](https://github.com/spring-projects/spring-boot/issues/52581) Nested WritableJson.toByteArray on the same thread corrupts  | [#51154](https://github.com/spring-projects/spring-boot/issues/51154) 0.79 | - | logs or stack trace | ok |  |  |
| 3 | [#52580](https://github.com/spring-projects/spring-boot/issues/52580) JsonWriter ignores a ValueProcessor implemented as a class | [#35891](https://github.com/spring-projects/spring-boot/issues/35891) 0.63 | - | logs or stack trace | nothing_relevant |  |  |
| 4 | [#52567](https://github.com/spring-projects/spring-boot/issues/52567) AcceptLanguage: More than the maximum (10) allowed number of | [#7930](https://github.com/spring-projects/spring-boot/issues/7930) 0.65 | - | steps to reproduce, logs or stack trace | nothing_relevant |  |  |
| 5 | [#52553](https://github.com/spring-projects/spring-boot/issues/52553) Binding a value from a config tree fails after a collection  | [#38734](https://github.com/spring-projects/spring-boot/issues/38734) 0.72 | - | nothing | nothing_relevant |  |  |
| 6 | [#52529](https://github.com/spring-projects/spring-boot/issues/52529) Support MockServer (https://www.mock-server.com) ServiceConn | [#52519](https://github.com/spring-projects/spring-boot/issues/52519) 0.81 | type: enhancement | nothing | ok |  |  |
| 7 | [#52521](https://github.com/spring-projects/spring-boot/issues/52521) Built-in support for P6spy | [#44010](https://github.com/spring-projects/spring-boot/issues/44010) 0.43 | type: dependency-upgrade | version | nothing_relevant |  |  |
| 8 | [#52520](https://github.com/spring-projects/spring-boot/issues/52520) Built-in support for Ryuk-like process in spring-boot-docker | [#35021](https://github.com/spring-projects/spring-boot/issues/35021) 0.65 | type: enhancement | nothing | ok |  |  |
| 9 | [#52519](https://github.com/spring-projects/spring-boot/issues/52519) Support MockServerConnectionDetails on DockerComposeService | [#45652](https://github.com/spring-projects/spring-boot/issues/45652) 0.73 | - | version, steps to reproduce | ok |  |  |
| 10 | [#52516](https://github.com/spring-projects/spring-boot/issues/52516) CacheAspectSupport initialisation does not support multiple  | [#37095](https://github.com/spring-projects/spring-boot/issues/37095) 0.74 | - | steps to reproduce, logs or stack trace | ok |  |  |
| 11 | [#52515](https://github.com/spring-projects/spring-boot/issues/52515) Support Optional<NestedClass> in @ConfigurationProperties | [#15999](https://github.com/spring-projects/spring-boot/issues/15999) 0.80 | - | steps to reproduce | ok |  |  |
| 12 | [#52514](https://github.com/spring-projects/spring-boot/issues/52514) spring-boot-starter-hazelcast does not include hazelcast-spr | [#19487](https://github.com/spring-projects/spring-boot/issues/19487) 0.81 | - | steps to reproduce, logs or stack trace | ok |  |  |
| 13 | [#52505](https://github.com/spring-projects/spring-boot/issues/52505) Consider publishing a JSON schema for Configuration Metadata | [#40315](https://github.com/spring-projects/spring-boot/issues/40315) 0.76 | - | nothing | ok |  |  |
| 14 | [#52500](https://github.com/spring-projects/spring-boot/issues/52500)  JSON parse failures no longer produce DockerOutputParseExce | [#52499](https://github.com/spring-projects/spring-boot/issues/52499) 0.98, LLM: UNSURE | type: bug | version, steps to reproduce, expected vs actual, logs or stack trace | ok |  |  |
| 15 | [#52499](https://github.com/spring-projects/spring-boot/issues/52499)  JSON parse failures no longer produce DockerOutputParseExce | [#38220](https://github.com/spring-projects/spring-boot/issues/38220) 0.75 | type: bug | version, steps to reproduce, expected vs actual, logs or stack trace | ok |  |  |
| 16 | [#52498](https://github.com/spring-projects/spring-boot/issues/52498) Register ObservationAwareBaggageThreadLocalAccessor so that  | [#35906](https://github.com/spring-projects/spring-boot/issues/35906) 0.66 | theme: observability | nothing | nothing_relevant |  |  |
| 17 | [#52486](https://github.com/spring-projects/spring-boot/issues/52486) Provide customizer callback for embedded LDAP's InMemoryDire | [#51437](https://github.com/spring-projects/spring-boot/issues/51437) 0.76 | type: enhancement | version | ok |  |  |
| 18 | [#52465](https://github.com/spring-projects/spring-boot/issues/52465) Caffeine cache auto-configuration ignores an AsyncCacheLoade | [#51843](https://github.com/spring-projects/spring-boot/issues/51843) 0.85, LLM: NO | - | logs or stack trace | ok |  |  |
| 19 | [#52401](https://github.com/spring-projects/spring-boot/issues/52401) Locale parsing regression in Spring Boot 4.1.2: Accept-Langu | [#6990](https://github.com/spring-projects/spring-boot/issues/6990) 0.63 | - | logs or stack trace | nothing_relevant |  |  |
| 20 | [#52400](https://github.com/spring-projects/spring-boot/issues/52400) [Security] Bump logback-classic to >= 1.6.3 due to CVE-2026- | [#38643](https://github.com/spring-projects/spring-boot/issues/38643) 0.79 | - | expected vs actual, logs or stack trace | ok |  |  |
| 21 | [#52394](https://github.com/spring-projects/spring-boot/issues/52394) Introspection setting silently ignored when a custom GraphQl | [#33096](https://github.com/spring-projects/spring-boot/issues/33096) 0.73 | type: enhancement | logs or stack trace | ok |  |  |
| 22 | [#52392](https://github.com/spring-projects/spring-boot/issues/52392) @RestController returning a POJO fails with NoClassDefFoundE | [#1190](https://github.com/spring-projects/spring-boot/issues/1190) 0.80 | - | nothing | ok |  |  |
| 23 | [#52390](https://github.com/spring-projects/spring-boot/issues/52390) Native image startup fails with Redisson on Spring Boot 4.1. | [#35587](https://github.com/spring-projects/spring-boot/issues/35587) 0.69 | - | nothing | ok |  |  |
| 24 | [#52379](https://github.com/spring-projects/spring-boot/issues/52379) OGNL should excluded when using thymeleaf-layout-dialect | [#6862](https://github.com/spring-projects/spring-boot/issues/6862) 0.72 | - | version | nothing_relevant |  |  |
| 25 | [#52366](https://github.com/spring-projects/spring-boot/issues/52366) GitHub Release notes missing for v4.1.2 | [#27692](https://github.com/spring-projects/spring-boot/issues/27692) 0.79 | - | version, expected vs actual | ok |  |  |
| 26 | [#52347](https://github.com/spring-projects/spring-boot/issues/52347) configprops endpoint prefix selector also returns beans of s | [#14880](https://github.com/spring-projects/spring-boot/issues/14880) 0.69 | - | nothing | ok |  |  |
| 27 | [#52320](https://github.com/spring-projects/spring-boot/issues/52320) Spring Batch JDBC auto-configuration connects to the databas | [#49368](https://github.com/spring-projects/spring-boot/issues/49368) 0.69 | - | steps to reproduce, expected vs actual, logs or stack trace | ok |  |  |
| 28 | [#52316](https://github.com/spring-projects/spring-boot/issues/52316) X-Forwarded-Prefix can no longer be enabled with the NATIVE  | [#10900](https://github.com/spring-projects/spring-boot/issues/10900) 0.69 | - | nothing | nothing_relevant |  |  |
| 29 | [#52302](https://github.com/spring-projects/spring-boot/issues/52302) OnAvailableEndpointCondition caches retain closed applicatio | [#50954](https://github.com/spring-projects/spring-boot/issues/50954) 0.67 | - | expected vs actual | ok |  |  |
| 30 | [#52301](https://github.com/spring-projects/spring-boot/issues/52301) Maven process-test-aot only copies META-INF/native-image of  | [#47837](https://github.com/spring-projects/spring-boot/issues/47837) 0.74 | - | logs or stack trace | ok |  |  |

## 1. [#52582](https://github.com/spring-projects/spring-boot/issues/52582) ClearCachesApplicationListener does not find inherited clearCache() methods

**Possible duplicates:** [#15964](https://github.com/spring-projects/spring-boot/issues/15964) org.springframework.boot.loader.jar.Handler rootFileCache can not be refreshed  (0.658); [#16536](https://github.com/spring-projects/spring-boot/issues/16536) Resources in /META-INF/ of executable-fat-jar would be fetched repeatedly. (0.626); [#31136](https://github.com/spring-projects/spring-boot/issues/31136) Failed to load ApplicationContext with JCache and pre-configured caches during unit tests (0.604)

**Labels:** none

**Missing details:** nothing

**Draft reply (ok):**

> Thanks for the detailed report. The reflective lookup analysis looks plausible, and the `getMethod` vs `getDeclaredMethod` distinction is exactly the kind of thing that fails silently. The Spring Boot caching docs (https://docs.spring.io/spring-boot/4.1/reference/io/caching.html#io.caching.provider) don't cover `ClearCachesApplicationListener` or `JarUrlClassLoader`, so I can't confirm the intended behaviour from them.
> 
> Could you share a minimal reproduction, or the stack trace or breakpoint output from the live refresh you mentioned? That would let us confirm the caches are retained in practice. Thanks for pointing at commit 7ad4a9817d as the likely origin.

Tokens: 4093

## 2. [#52581](https://github.com/spring-projects/spring-boot/issues/52581) Nested WritableJson.toByteArray on the same thread corrupts both results

**Possible duplicates:** [#51154](https://github.com/spring-projects/spring-boot/issues/51154) Structured logging: a failed JSON encode corrupts the next log event written on the same thread (0.785); [#49428](https://github.com/spring-projects/spring-boot/issues/49428) Reduce memory consumption when repeatedly calling WritableJson.toByteArray (0.660); [#44502](https://github.com/spring-projects/spring-boot/issues/44502) Logging a Path object using structured logging throws StackOverflowError (0.657)

**Labels:** none

**Missing details:** logs or stack trace

**Draft reply (ok):**

> Thanks for the detailed report and minimal reproduction. The nested `toByteArray` collision on the per-thread `AppendableByteArray` cache is a clear explanation of the corrupted output you're seeing.
> 
> This looks related to issue #51154, which describes a failed JSON encode corrupting the next log event written on the same thread. It may be the same root cause or a close cousin, so it would be worth comparing the two. The structured logging docs are at https://docs.spring.io/spring-boot/4.1/reference/features/logging.html#features.logging.structured, though they don't cover this internal buffer behaviour.
> 
> We haven't reproduced this yet, so we can't confirm the fix you proposed. Your mention of differing console and file level thresholds is useful and we'll keep it in mind.

Tokens: 4341

## 3. [#52580](https://github.com/spring-projects/spring-boot/issues/52580) JsonWriter ignores a ValueProcessor implemented as a class

**Possible duplicates:** [#35891](https://github.com/spring-projects/spring-boot/issues/35891) Jackson's ObjectMapper writes empty JSON strings for Spring Boot 3.1.0 if the entity is a record (0.628); [#9553](https://github.com/spring-projects/spring-boot/issues/9553) JSON deserialization fails with presence of `jackson-module-parameter-names` (0.587); [#50932](https://github.com/spring-projects/spring-boot/issues/50932) Spring Boot 4 / Jackson 3 ignores field default value during @RequestBody deserialization (0.580)

**Labels:** none

**Missing details:** logs or stack trace

**Draft reply (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

Tokens: 1693

## 4. [#52567](https://github.com/spring-projects/spring-boot/issues/52567) AcceptLanguage: More than the maximum (10) allowed number of locales

**Possible duplicates:** [#7930](https://github.com/spring-projects/spring-boot/issues/7930) Allow supported locales to be configured via the environment (0.645); [#8682](https://github.com/spring-projects/spring-boot/issues/8682) spring-boot-starter-tomcat 2.0 failed to start embedded Tomcat (0.641); [#49311](https://github.com/spring-projects/spring-boot/issues/49311) Tomcat's default max part count is too low in 4.0.x (0.636)

**Labels:** none

**Missing details:** steps to reproduce, logs or stack trace

**Draft reply (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

Tokens: 581

## 5. [#52553](https://github.com/spring-projects/spring-boot/issues/52553) Binding a value from a config tree fails after a collection of the same element type has been bound from the same config tree

**Possible duplicates:** [#38734](https://github.com/spring-projects/spring-boot/issues/38734) Custom binding converters are ignored when working with collection types (0.722); [#16290](https://github.com/spring-projects/spring-boot/issues/16290) Binding to collection fails with unbound elements error if collection overridden in another property source (0.680); [#24171](https://github.com/spring-projects/spring-boot/issues/24171) No converter is found when binding a non-string property via config tree  (0.657)

**Labels:** none

**Missing details:** nothing

**Draft reply (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

Tokens: 2247

## 6. [#52529](https://github.com/spring-projects/spring-boot/issues/52529) Support MockServer (https://www.mock-server.com) ServiceConnection

**Possible duplicates:** [#52519](https://github.com/spring-projects/spring-boot/issues/52519) Support MockServerConnectionDetails on DockerComposeService (0.807); [#34747](https://github.com/spring-projects/spring-boot/issues/34747) Provide support for auto-configuring service connections from Docker Compose (0.759); [#45652](https://github.com/spring-projects/spring-boot/issues/45652) Make integration testing of webclient easier with @MockServerResponse annotation. (0.750)

**Labels:** type: enhancement (59%)

**Missing details:** nothing

**Draft reply (ok):**

> Thanks for the suggestion. The sources I have cover Testcontainers service connections and Docker Compose support in general, but they don't document how MockServer would be detected or wired up, so I can't confirm whether it's already supported or what the right approach would be. The Testcontainers service connection docs list which container types get built-in connection details: https://docs.spring.io/spring-boot/4.1/reference/testing/testcontainers.html#testing.testcontainers.service-connections
> 
> It looks like you already have a tentative implementation in #52519, which would be the natural place to discuss the details. Could you share the Spring Boot version you're using and the Docker Compose service definition you'd expect to be detected? That would help the maintainers evaluate the request.

Tokens: 2823

## 7. [#52521](https://github.com/spring-projects/spring-boot/issues/52521) Built-in support for P6spy

**Possible duplicates:** [#44010](https://github.com/spring-projects/spring-boot/issues/44010) Upgrade to Jboss Transaction Spi 7.6.1.Final (0.429); [#35740](https://github.com/spring-projects/spring-boot/issues/35740) Upgrade to Spring Integration 6.0.6 (0.408); [#33146](https://github.com/spring-projects/spring-boot/issues/33146) Upgrade to Spring Integration 6.0.0 (0.405)

**Labels:** type: dependency-upgrade (79%)

**Missing details:** version

**Draft reply (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

Tokens: 468

## 8. [#52520](https://github.com/spring-projects/spring-boot/issues/52520) Built-in support for Ryuk-like process in spring-boot-docker-compose

**Possible duplicates:** [#35021](https://github.com/spring-projects/spring-boot/issues/35021) Support testcontainers lifecycle methods when used as Spring beans (0.652); [#35661](https://github.com/spring-projects/spring-boot/issues/35661) Document how to use Docker Compose integration when running tests (0.626); [#35210](https://github.com/spring-projects/spring-boot/issues/35210) Don't call close methods for reusable testcontainers (0.588)

**Labels:** type: enhancement (60%)

**Missing details:** nothing

**Draft reply (ok):**

> Thanks for the suggestion and for the detailed reasoning. The Spring Boot reference on Testcontainers describes how containers are started and stopped with the application context: containers managed by Spring are shut down as part of the TestContext Framework's standard shutdown, and Testcontainers-managed containers have no ordering guarantee relative to beans (https://docs.spring.io/spring-boot/4.1/reference/testing/testcontainers.html#testing.testcontainers.lifecycle). Those docs don't cover cleanup after a SIGKILL or machine interruption, nor whether a Ryuk-like mechanism is planned for spring-boot-docker-compose, so I can't speak to that. Could you share your Spring Boot version, your Docker Compose setup, and the steps to reproduce the leftover containers? That would help the maintainers evaluate the request.

Tokens: 2145

## 9. [#52519](https://github.com/spring-projects/spring-boot/issues/52519) Support MockServerConnectionDetails on DockerComposeService

**Possible duplicates:** [#45652](https://github.com/spring-projects/spring-boot/issues/45652) Make integration testing of webclient easier with @MockServerResponse annotation. (0.730); [#34501](https://github.com/spring-projects/spring-boot/issues/34501) Allow MockServerRestTemplateCustomizer to configure MockRestServiceServer's content buffering (0.649); [#35405](https://github.com/spring-projects/spring-boot/issues/35405) Spring-boot-docker-compose with Docker in Docker (0.646)

**Labels:** none

**Missing details:** version, steps to reproduce

**Draft reply (ok):**

> Thanks for the detailed write-up. The sources I have cover Spring Boot's Testcontainers service connections and mock environment testing, not Docker Compose connection details, so I can't confirm whether built-in MockServer support is planned or how it should be designed.
> 
> The closest relevant material is the Testcontainers service connections section, which lists the connection detail types provided out of the box and how factories are registered: https://docs.spring.io/spring-boot/4.1/reference/testing/testcontainers.html#testing.testcontainers.service-connections
> 
> Could you tell us which Spring Boot version you're on and whether your factory works as expected? That would help the team evaluate the request.

Tokens: 3769

## 10. [#52516](https://github.com/spring-projects/spring-boot/issues/52516) CacheAspectSupport initialisation does not support multiple CacheManager

**Possible duplicates:** [#37095](https://github.com/spring-projects/spring-boot/issues/37095) CacheMetricsRegistrarConfiguration fails to start if there is a request scoped cache manager in the context (0.740); [#5201](https://github.com/spring-projects/spring-boot/issues/5201) Allow custom `CacheResolver` to be configured (0.736); [#8467](https://github.com/spring-projects/spring-boot/issues/8467) early bean initialization of cache-related beans lead to early creating of the cache manager (0.729)

**Labels:** none

**Missing details:** steps to reproduce, logs or stack trace

**Draft reply (ok):**

> Thanks for the report. The Spring Boot docs (https://docs.spring.io/spring-boot/4.1/reference/io/caching.html#io.caching.provider) say that Boot only auto-configures a CacheManager when you have not defined your own CacheManager or a CacheResolver bean named `cacheResolver`, and that you can customize the auto-configured manager through CacheManagerCustomizer. The docs don't cover how the `cacheManager` attribute on `@Cacheable` interacts with multiple CacheManager beans, so I can't confirm whether the startup failure you describe is expected behaviour or a bug. Could you share a minimal reproducer, your full stack trace, and whether the same setup works on 4.1.x? That would help the maintainers look into it.

Tokens: 2984

## 11. [#52515](https://github.com/spring-projects/spring-boot/issues/52515) Support Optional<NestedClass> in @ConfigurationProperties

**Possible duplicates:** [#15999](https://github.com/spring-projects/spring-boot/issues/15999) Optional nested configuration property not assigned (0.804); [#281](https://github.com/spring-projects/spring-boot/issues/281) @ConfigurationProperties should have the possibilty to not allow null values (0.766); [#19803](https://github.com/spring-projects/spring-boot/issues/19803) Provide empty Optional for missing values when using @ConstructorBinding (0.762)

**Labels:** none

**Missing details:** steps to reproduce

**Draft reply (ok):**

> Thanks for the report. The docs say that an Optional constructor-bound property with no value is bound as an empty Optional (https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.constructor-binding), and that default values are applied to missing properties via @DefaultValue (https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.constructor-binding.default-values). Related earlier discussions include https://github.com/spring-projects/spring-boot/issues/15999 and https://github.com/spring-projects/spring-boot/issues/19803.
> 
> I can't tell from these sources whether Optional<NestedClass> is supported in this case, so this may be a limitation or a bug. To help us investigate, could you share a minimal reproducer, including your record definition and the exact binding result you get on 4.2.0-M2? Please also confirm whether the nested block is absent or present but empty in the YAML.

Tokens: 3219

## 12. [#52514](https://github.com/spring-projects/spring-boot/issues/52514) spring-boot-starter-hazelcast does not include hazelcast-spring dependency

**Possible duplicates:** [#19487](https://github.com/spring-projects/spring-boot/issues/19487) Create Hazelcast config with SpringManagedContext when available (0.815); [#20856](https://github.com/spring-projects/spring-boot/issues/20856) Add support for Hazelcast 4 (0.810); [#4389](https://github.com/spring-projects/spring-boot/issues/4389) CacheAutoConfiguration tries to configure CacheManager before Hazelcast is started (0.804)

**Labels:** none

**Missing details:** steps to reproduce, logs or stack trace

**Draft reply (ok):**

> Thanks for the report. The Hazelcast docs describe the caching integration: Spring Boot wraps an auto-configured HazelcastInstance in a CacheManager only when `com.hazelcast:hazelcast-spring` is on the classpath (see https://docs.spring.io/spring-boot/4.1/reference/io/caching.html#io.caching.provider.hazelcast). Setting `spring.cache.type` to `hazelcast` selects that CacheManager-based implementation.
> 
> This doesn't directly address whether the starter should bring in that dependency, so the maintainers may need to check the starter's intended contents. To help reproduce this, could you share the output of your dependency tree (for example, `mvn dependency:tree` or `gradle dependencies`), your `spring.cache.type` setting, and the full startup log around the cache configuration?

Tokens: 2826

## 13. [#52505](https://github.com/spring-projects/spring-boot/issues/52505) Consider publishing a JSON schema for Configuration Metadata

**Possible duplicates:** [#40315](https://github.com/spring-projects/spring-boot/issues/40315) Provide an alternative to javadoc for documenting configuration properties (0.755); [#48864](https://github.com/spring-projects/spring-boot/issues/48864) Consider to provide the information for the configuration processor in the form of annotations (0.701); [#17570](https://github.com/spring-projects/spring-boot/issues/17570) Provider a helper method in spring-boot-configuration-metadata that maps from a property name or similar to a metadata entry (0.694)

**Labels:** none

**Missing details:** nothing

**Draft reply (ok):**

> Thanks for the detailed proposal. The sources I have cover how Spring Boot binds configuration properties and how @ConfigurationProperties validation and metadata work, but they don't describe the Configuration Metadata file format itself or any JSON Schema for it, so I can't confirm the current state of that format from them. The linked Metadata Format and Providing Manual Hints pages are the right place to check what's documented today.
> 
> For background on how metadata is produced and consumed, see https://docs.spring.io/spring-boot/4.1/reference/features/external-config.html#features.external-config.typesafe-configuration-properties.using-annotated-types, which notes that @ConfigurationProperties types enable IDE auto-completion via generated metadata. Related discussion of documenting configuration properties is in https://github.com/spring-projects/spring-boot/issues/40315.
> 
> Could you share a sample manual hints document and the validation errors you'd like to catch? That would help us judge what a schema needs to cover.

Tokens: 3234

## 14. [#52500](https://github.com/spring-projects/spring-boot/issues/52500)  JSON parse failures no longer produce DockerOutputParseExceptions

**Possible duplicates:** [#52499](https://github.com/spring-projects/spring-boot/issues/52499)  JSON parse failures no longer produce DockerOutputParseExceptions (0.984); [#38220](https://github.com/spring-projects/spring-boot/issues/38220) Docker JSON parsing fails on certain locales (0.764); [#44630](https://github.com/spring-projects/spring-boot/issues/44630) Docker API error message is missing in some cases (0.744)
  - LLM check of the top match: **UNSURE**: Both issues have identical titles and are forward ports of related issues to different branches (4.2.x and 4.1.x), but the bodies give no detail of the actual problem, so it is unclear whether they describe the same root bug or just the same fix being backported across branches.

**Labels:** type: bug (72%)

**Missing details:** version, steps to reproduce, expected vs actual, logs or stack trace

**Already fixed?** #52499 was fixed in 4.1.3. The reporter's version isn't in the issue: ask for it.

**Draft reply (ok):**

> Thanks for opening this. It looks like a forward port of #52499 to 4.2.x, which is already tracked at https://github.com/spring-projects/spring-boot/issues/52499. The related report at https://github.com/spring-projects/spring-boot/issues/38220 covers Docker JSON parsing failures on certain locales, which may be relevant context.
> 
> I can't confirm from these sources what the 4.2.x status or timeline is, so a maintainer will need to weigh in on whether this should proceed as a separate backport. Thanks for your patience.

Tokens: 1502

## 15. [#52499](https://github.com/spring-projects/spring-boot/issues/52499)  JSON parse failures no longer produce DockerOutputParseExceptions

**Possible duplicates:** [#38220](https://github.com/spring-projects/spring-boot/issues/38220) Docker JSON parsing fails on certain locales (0.752); [#47901](https://github.com/spring-projects/spring-boot/issues/47901) Docker response 407 is not handled correctly resulting in no error message (0.748); [#44630](https://github.com/spring-projects/spring-boot/issues/44630) Docker API error message is missing in some cases (0.735)

**Labels:** type: bug (61%)

**Missing details:** version, steps to reproduce, expected vs actual, logs or stack trace

**Already fixed?** #38220 was fixed in 3.2.0. The reporter's version isn't in the issue: ask for it.

**Draft reply (ok):**

> Thanks for the report! This looks related to #38220, which covers Docker JSON parsing failing under certain locales (https://github.com/spring-projects/spring-boot/issues/38220). However, the new issue refers to #52380, which isn't among the sources I can check, so I can't confirm how the two relate or what changed in 4.1.x.
> 
> To help us triage, could you share the 4.1.x version you're on, the exception or log output you're seeing, and a short description of what behavior changed compared with the earlier release? If the forward-port reason is clear from #52380, a link to it would also help.

Tokens: 994

## 16. [#52498](https://github.com/spring-projects/spring-boot/issues/52498) Register ObservationAwareBaggageThreadLocalAccessor so that baggage created with the Tracer API survives thread hops

**Possible duplicates:** [#35906](https://github.com/spring-projects/spring-boot/issues/35906) Automatically register the ObservationAwareSpanThreadLocalAccessor after ObservationThreadLocalAccessor (0.661); [#47893](https://github.com/spring-projects/spring-boot/issues/47893) Document how to use ContextPropagatingTaskDecorator for propagating trace context over thread boundaries (0.643); [#48053](https://github.com/spring-projects/spring-boot/issues/48053) Document how to use ContextPropagatingTaskDecorator for propagating trace context over thread boundaries (0.627)

**Labels:** theme: observability (62%)

**Missing details:** nothing

**Draft reply (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

Tokens: 1064

## 17. [#52486](https://github.com/spring-projects/spring-boot/issues/52486) Provide customizer callback for embedded LDAP's InMemoryDirectoryServerConfig

**Possible duplicates:** [#51437](https://github.com/spring-projects/spring-boot/issues/51437) Add property to configure required authentication operation types for embedded LDAP (0.758); [#48060](https://github.com/spring-projects/spring-boot/issues/48060) Add support for LDAPS testing (0.677); [#7929](https://github.com/spring-projects/spring-boot/issues/7929) Auto-configure an ActiveDirectoryLdapAuthenticationProvider from configuration properties (0.652)

**Labels:** type: enhancement (61%)

**Missing details:** version

**Draft reply (ok):**

> Thanks for the detailed proposal, and for the kind words about #51437.
> 
> The current docs cover the embedded LDAP server: adding `com.unboundid:unboundid-ldapsdk`, setting `spring.ldap.embedded.base-dn`, and optionally loading initial data via `schema.ldif` or `spring.ldap.embedded.ldif`. See https://docs.spring.io/spring-boot/4.1/reference/data/nosql.html#data.nosql.ldap.embedded
> 
> The sources I have don't describe any hook for registering custom `InMemoryOperationInterceptor` instances, so I can't confirm whether either option is already supported. Could you let us know which Spring Boot version you're on and whether you've tried any approach beyond the documented properties? Your Option 1 and Option 2 sketches would be useful to discuss with the maintainers.

Tokens: 2639

## 18. [#52465](https://github.com/spring-projects/spring-boot/issues/52465) Caffeine cache auto-configuration ignores an AsyncCacheLoader bean in 4.2

**Possible duplicates:** [#51843](https://github.com/spring-projects/spring-boot/issues/51843) Expose Caffeine async cache mode via a configuration property (0.846); [#7594](https://github.com/spring-projects/spring-boot/issues/7594) CacheManagerJpaDependencyConfiguration is looking for a bean named "cacheManager" instead of type CacheManager (0.784); [#16823](https://github.com/spring-projects/spring-boot/issues/16823) Better Caffeine Configuration (0.759)
  - LLM check of the top match: **NO**: The earlier issue requests exposing async cache mode via a property, while the new issue reports that an AsyncCacheLoader bean is ignored by auto-configuration, a different bug in loader wiring even though it builds on the same feature.

**Labels:** none

**Missing details:** logs or stack trace

**Draft reply (ok):**

> Thanks for the report and the clear reproduction. Based on the Spring Boot reference documentation, Caffeine customization is driven by `spring.cache.caffeine.spec`, a `CaffeineSpec` bean, or a `Caffeine` bean, and a `CacheLoader<Object, Object>` bean is associated with the cache manager automatically; other generic types are ignored (https://docs.spring.io/spring-boot/4.1/reference/io/caching.html#io.caching.provider.caffeine). The docs don't describe an `AsyncCacheLoader` bean being picked up, so this looks like a gap in the auto-configuration rather than documented behavior. Related async-mode discussion is in https://github.com/spring-projects/spring-boot/issues/51843. A PR would be welcome; please let us know if you'd like guidance on where the change should go.

Tokens: 4390

## 19. [#52401](https://github.com/spring-projects/spring-boot/issues/52401) Locale parsing regression in Spring Boot 4.1.2: Accept-Language: zh-Hans-CN;q=1.0 produces wrong toLanguageTag() output

**Possible duplicates:** [#6990](https://github.com/spring-projects/spring-boot/issues/6990) spring.mvc.locale is ignored (0.634); [#7930](https://github.com/spring-projects/spring-boot/issues/7930) Allow supported locales to be configured via the environment (0.619); [#1228](https://github.com/spring-projects/spring-boot/issues/1228) Encoding error when using spring-boot 1.1.3, 1.1.4 or 1.1.5 but works great in 1.0.2 version (0.590)

**Labels:** none

**Missing details:** logs or stack trace

**Draft reply (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

Tokens: 1031

## 20. [#52400](https://github.com/spring-projects/spring-boot/issues/52400) [Security] Bump logback-classic to >= 1.6.3 due to CVE-2026-19880 (Path Traversal)

**Possible duplicates:** [#38643](https://github.com/spring-projects/spring-boot/issues/38643) CVE for logback 1.4.11 with the latest SpringBoot 3.2 (0.790); [#8635](https://github.com/spring-projects/spring-boot/issues/8635) Update logback to v1.2.x - CVE-2017-5929 (0.784); [#38926](https://github.com/spring-projects/spring-boot/issues/38926) CVEs for logback 1.2.12 in the latest version of spring boot 2.7.x (2.7.18) (0.757)

**Labels:** none

**Missing details:** expected vs actual, logs or stack trace

**Draft reply (ok):**

> Thanks for the report. Our sources cover earlier, related Logback CVE discussions but do not address CVE-2026-19880 or the 1.6.3 version you mention, so I can't confirm the fix version or its status from them. Related prior threads are https://github.com/spring-projects/spring-boot/issues/38643 and https://github.com/spring-projects/spring-boot/issues/38926, which may give context on how Logback upgrades have been handled. To help us triage, could you share the NVD or advisory link for CVE-2026-19880 and confirm the exact Spring Boot version you are using (the 4.1.2 path in your report)? Thank you for the detailed description.

Tokens: 2314

## 21. [#52394](https://github.com/spring-projects/spring-boot/issues/52394) Introspection setting silently ignored when a custom GraphQlSource bean is present

**Possible duplicates:** [#33096](https://github.com/spring-projects/spring-boot/issues/33096) GraphQL auto-configuration should not back off when existing GraphQlSource bean (0.730); [#29248](https://github.com/spring-projects/spring-boot/issues/29248) Provide option for disabling field introspection with GraphQL (0.710); [#36252](https://github.com/spring-projects/spring-boot/issues/36252) Add configuration property for GraphQL Schema Mapping Inspection (0.695)

**Labels:** type: enhancement (51%)

**Missing details:** logs or stack trace

**Draft reply (ok):**

> Thanks for the detailed report and the reproduction test. The behaviour you describe matches how the autoconfigured `graphQlSource` method backs off when a custom `GraphQlSource` bean is present, so the introspection setting is never applied in that case. The docs on schema introspection are at https://docs.spring.io/spring-boot/4.1/reference/web/spring-graphql.html#web.graphql.schema, and they describe the property without mentioning this interaction.
> 
> Your two proposed resolutions look like good starting points for discussion. A maintainer will take a closer look at whether the setting should apply regardless of who creates the `GraphQlSource`, or whether a startup warning is the better fit.
> 
> Thanks again for the clear write-up.

Tokens: 5447

## 22. [#52392](https://github.com/spring-projects/spring-boot/issues/52392) @RestController returning a POJO fails with NoClassDefFoundError: com/fasterxml/jackson/annotation/JsonApplyView on Spring Boot 4.1.2 / Jackson 3.2.2

**Possible duplicates:** [#1190](https://github.com/spring-projects/spring-boot/issues/1190) Broken Jackson getter serialization. (0.798); [#7785](https://github.com/spring-projects/spring-boot/issues/7785) 1.4.3 spring-boot-starter-web failed to support jackson message converter (0.763); [#24233](https://github.com/spring-projects/spring-boot/issues/24233) @JsonCreator factory ignored when deserializing enums (0.719)

**Labels:** none

**Missing details:** nothing

**Already fixed?** #1190 was fixed in 1.1.4, but the reporter already uses 4.1.2: a regression, or a different problem.

**Draft reply (ok):**

> Thanks for the detailed report. The sources I have cover how Spring Boot 4.1 handles JSON responses and Jackson configuration, but they don't address this specific class-loading failure or the version mismatch between jackson-databind and jackson-annotations, so I can't confirm the cause from them.
> 
> Useful context for the maintainers: the default JSON setup is described at https://docs.spring.io/spring-boot/4.1/how-to/spring-mvc.html#howto.spring-mvc.write-json-rest-service, and the isolated Actuator JsonMapper is at https://docs.spring.io/spring-boot/4.1/reference/actuator/endpoints.html#actuator.endpoints.json.
> 
> Could you run `mvn dependency:tree -Dincludes=com.fasterxml.jackson.core` and confirm whether any other Jackson 2.x artifact appears on the classpath? Also, does the error reproduce with a minimal project and no other dependencies?

Tokens: 6121

## 23. [#52390](https://github.com/spring-projects/spring-boot/issues/52390) Native image startup fails with Redisson on Spring Boot 4.1.2: io.netty.util.ReferenceCountUtil cannot initialize at run time

**Possible duplicates:** [#35587](https://github.com/spring-projects/spring-boot/issues/35587) Spring boot3.0  using Redisson can not startup when AOT compile (0.694); [#35586](https://github.com/spring-projects/spring-boot/issues/35586) Spring boot3.0  using Redisson can not lauch when AOT compile (0.686); [#42977](https://github.com/spring-projects/spring-boot/issues/42977) GraalVM native image - Flyway prevents startups (0.669)

**Labels:** none

**Missing details:** nothing

**Draft reply (ok):**

> Thanks for the detailed report and reproduction steps. The sources I have cover building and testing native images with Maven and the general known-limitations guidance, but they don't address this specific startup failure or the difference between Spring Boot 4.1.1 and 4.1.2, so I can't offer a diagnosis from them.
> 
> Useful next steps are the Spring Boot with GraalVM wiki page, https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-with-GraalVM, and the spring-aot-smoke-tests project. If the problem is in a third-party library's native metadata, the reachability metadata project is the right place to raise it.
> 
> Could you share the full stack trace, including the original `ExceptionInInitializerError` if you can capture it (for example with `-H:+ReportExceptionStackTraces` at image build time), and confirm whether the minimal reproduction in jfoundry fails on a clean checkout? That would help the maintainers narrow this down.

Links not among the sources: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-with-GraalVM

Tokens: 6436

## 24. [#52379](https://github.com/spring-projects/spring-boot/issues/52379) OGNL should excluded when using thymeleaf-layout-dialect

**Possible duplicates:** [#6862](https://github.com/spring-projects/spring-boot/issues/6862) Add Thymeleaf with dialect (0.716); [#7557](https://github.com/spring-projects/spring-boot/issues/7557) Remove thymeleaf-layout-dialect from spring-boot-starter-thymeleaf (0.686); [#12889](https://github.com/spring-projects/spring-boot/issues/12889) Spring Boot 2.0 Migration Guide Additions (0.663)

**Labels:** none

**Missing details:** version

**Draft reply (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

Tokens: 712

## 25. [#52366](https://github.com/spring-projects/spring-boot/issues/52366) GitHub Release notes missing for v4.1.2

**Possible duplicates:** [#27692](https://github.com/spring-projects/spring-boot/issues/27692) Spring Boot 2.2 Release Notes missing from Wiki (0.790); [#1621](https://github.com/spring-projects/spring-boot/issues/1621) Spring Boot 1.1.x docs link to Spring 4.1's docs rather than 4.0.x's docs (0.665); [#51742](https://github.com/spring-projects/spring-boot/issues/51742) Wiki home lists outdated actively maintained Spring Boot versions (0.664)

**Labels:** none

**Missing details:** version, expected vs actual

**Draft reply (ok):**

> Thanks for raising this. The release notes for Spring Boot have historically lived on the project wiki rather than on the GitHub release page, so the empty v4.1.2 release page may be expected. The Spring Boot reference documentation also points to the wiki for the "new and noteworthy" summaries: see https://docs.spring.io/spring-boot/4.1/upgrading.html#upgrading.from-2x and the release notes section linked from there.
> 
> Could you confirm whether the wiki release notes for 4.1.2 are available? If they are not, that would be useful to know. Thanks for your help with the project.

Tokens: 2717

## 26. [#52347](https://github.com/spring-projects/spring-boot/issues/52347) configprops endpoint prefix selector also returns beans of sibling prefixes (spring.data matches spring.datasource)

**Possible duplicates:** [#14880](https://github.com/spring-projects/spring-boot/issues/14880) Provide end-to-end tracability for bound @ConfigurationProperties (0.687); [#18487](https://github.com/spring-projects/spring-boot/issues/18487) Claify ConfigurationPropertiesBean javadoc (0.682); [#1747](https://github.com/spring-projects/spring-boot/issues/1747) /configprops endpoint doesn't render maps or collections (0.666)

**Labels:** none

**Missing details:** nothing

**Draft reply (ok):**

> Thanks for the report. The `configprops` endpoint is documented in https://docs.spring.io/spring-boot/4.1/reference/actuator/endpoints.html#actuator.endpoints, and the sources I have don't describe how the prefix selector matches, so I can't confirm the `startsWith` behavior from them. A reproduction with the exact application setup and the `/actuator/configprops/spring.data` output would help the maintainers check it. Thanks also for the offer of a patch with a regression test against `3.5.x`; the maintainers can say whether they'd like it submitted as a pull request.

Tokens: 4032

## 27. [#52320](https://github.com/spring-projects/spring-boot/issues/52320) Spring Batch JDBC auto-configuration connects to the database on startup to detect its type, with no way to configure it

**Possible duplicates:** [#49368](https://github.com/spring-projects/spring-boot/issues/49368) Spring batch auto-config allows the JobRegistry to be used as a Spring Bean (0.694); [#8877](https://github.com/spring-projects/spring-boot/issues/8877) Batch auto-configuration requires JPA on the classpath (0.685); [#17451](https://github.com/spring-projects/spring-boot/issues/17451) Batch auto-configuration backs off too aggressively without a dependency on spring-jdbc (0.681)

**Labels:** none

**Missing details:** steps to reproduce, expected vs actual, logs or stack trace

**Draft reply (ok):**

> Thanks for the detailed report. Here's what the current docs say that's relevant: Spring Batch's JDBC schema initialization can be configured with spring.batch.jdbc.initialize-schema, and Spring Boot automatically detects beans that depend on database initialization (https://docs.spring.io/spring-boot/4.1/how-to/data-initialization.html#howto.data-initialization.batch). Spring Batch's own documentation describes the @EnableJdbcJobRepository and DefaultBatchConfiguration route for taking full control of its configuration (https://docs.spring.io/spring-boot/4.1/reference/io/spring-batch.html#io.spring-batch).
> 
> However, I don't see anything in these sources that addresses detecting the database type at startup or supplying it through a property, so they don't settle whether a spring.batch.jdbc.database-type property is the right approach. Could you confirm which Spring Boot version you're on and whether a database-type property has been discussed elsewhere? That would help the maintainers evaluate the proposal.

Tokens: 3419

## 28. [#52316](https://github.com/spring-projects/spring-boot/issues/52316) X-Forwarded-Prefix can no longer be enabled with the NATIVE strategy on Reactor Netty

**Possible duplicates:** [#10900](https://github.com/spring-projects/spring-boot/issues/10900) Reactor Netty does not support X-Forwarded-* request headers (0.691); [#43577](https://github.com/spring-projects/spring-boot/issues/43577) Breaking change on forward-headers-strategy (0.675); [#51030](https://github.com/spring-projects/spring-boot/issues/51030) Update properties for forwarded headers to adapt to Spring Framework changes (0.660)

**Labels:** none

**Missing details:** nothing

**Draft reply (nothing_relevant):**

> No docs section or earlier issue is similar enough to base a reply on, so there is no draft.

Tokens: 1008

## 29. [#52302](https://github.com/spring-projects/spring-boot/issues/52302) OnAvailableEndpointCondition caches retain closed application contexts through their Environment

**Possible duplicates:** [#50954](https://github.com/spring-projects/spring-boot/issues/50954) Closed ApplicationContexts are retained after eviction from the test context cache, causing OutOfMemoryError in large @SpringBootTest suites (0.671); [#21458](https://github.com/spring-projects/spring-boot/issues/21458) Multicontext JCache cache is closed (0.660); [#47299](https://github.com/spring-projects/spring-boot/issues/47299) Testcontainer service connections can be incorrectly cached when environment map changes (0.656)

**Labels:** none

**Missing details:** expected vs actual

**Draft reply (ok):**

> Thank you for the detailed report and the heap dump analysis.
> 
> The sources I have are the Spring Boot 4.1 Actuator endpoints reference (https://docs.spring.io/spring-boot/4.1/reference/actuator/endpoints.html#actuator.endpoints). They describe which endpoints exist and what they expose, but they don't cover the internals of OnAvailableEndpointCondition, its static caches, or how Environment references are held, so they don't directly address this issue.
> 
> To help triage, could you confirm whether the retention also reproduces on the 4.1.x branch with the reproducer as written, and whether the cut-off part of the test class contains any additional cleanup logic?

Tokens: 4695

## 30. [#52301](https://github.com/spring-projects/spring-boot/issues/52301) Maven process-test-aot only copies META-INF/native-image of the generated resources so the Logback configuration is lost in native tests

**Possible duplicates:** [#47837](https://github.com/spring-projects/spring-boot/issues/47837) Caused by: java.nio.file.FileAlreadyExistsException: target/spring-aot/test/resources/META-INF/spring/logback-model (0.735); [#41441](https://github.com/spring-projects/spring-boot/issues/41441) spring.aot.enabled=true breaks logback from loading (0.717); [#33624](https://github.com/spring-projects/spring-boot/issues/33624) Maven GraalVm native tests (mvn -PnativeTest test) (0.695)

**Labels:** none

**Missing details:** logs or stack trace

**Draft reply (ok):**

> Thanks for the detailed report and the reproducer. The issue describes `ProcessTestAotMojo` copying only `META-INF/native-image` from the generated resources, so `META-INF/spring/logback-model` never reaches the native test image. The contrast with `ProcessAotMojo` and the Gradle `aotTest` source set is useful context. The workaround you shared is helpful for anyone blocked in the meantime. Thanks for taking the time to write this up; a maintainer will look into it. Could you confirm which Native Build Tools version you're using, in case that matters?

Tokens: 5610
