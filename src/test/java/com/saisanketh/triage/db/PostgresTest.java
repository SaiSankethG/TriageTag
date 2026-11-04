package com.saisanketh.triage.db;

import com.saisanketh.triage.service.IssueService;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

/**
 * Base class for tests against a real Postgres (Testcontainers).
 *
 * <ul>
 *   <li>{@code @DataJpaTest}: starts only the JPA part of the app (entities, repositories, Flyway), not the web layer.
 *       Each test runs in a transaction that is rolled back at the end, so tests don't see each other's rows.</li>
 *   <li>{@code replace = NONE}: use our Postgres container, don't swap in an in-memory database.</li>
 *   <li>Flyway runs V1, V2... on the empty container, and Hibernate {@code validate} then checks the entities
 *       against that schema, so every real-DB test also tests the migrations.</li>
 *   <li>All subclasses have the same configuration, so Spring reuses one context and one container for all of them.</li>
 * </ul>
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresContainerConfig.class, IssueService.class})
public abstract class PostgresTest {
}
