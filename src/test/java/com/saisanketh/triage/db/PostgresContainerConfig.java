package com.saisanketh.triage.db;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real, throw-away Postgres 16 in Docker for tests (the same major version as docker-compose.yml).
 * As a Spring bean, it starts when the test context starts and stops with it. {@code @ServiceConnection}
 * points the app's DataSource at it, so no URL/user/password needs to be configured.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
    }
}
