package com.digitalbluebird.identity.adapter.outbound.persistence

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.digitalbluebird.identity.domain.Email
import com.digitalbluebird.identity.domain.User
import com.digitalbluebird.identity.domain.UserRole
import com.digitalbluebird.shared.domain.UserId
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcUserRepositoryIntegrationTest {

    private val postgres = PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("bird_platform")
        .withUsername("bird_platform")
        .withPassword("bird_platform")

    private lateinit var repository: JdbcUserRepository
    private lateinit var jdbc: JdbcTemplate

    @BeforeAll
    fun setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable, "Docker unavailable — skipping integration test")
        postgres.start()

        val dataSource = DriverManagerDataSource(
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        ).apply { setDriverClassName("org.postgresql.Driver") }

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration/shared", "classpath:db/migration/identity")
            .load()
            .migrate()

        jdbc = JdbcTemplate(dataSource)
        repository = JdbcUserRepository(NamedParameterJdbcTemplate(dataSource))
    }

    @AfterAll
    fun tearDown() {
        if (postgres.isRunning) postgres.stop()
    }

    @BeforeEach
    fun clean() {
        jdbc.execute("TRUNCATE TABLE identity.users")
    }

    @Test
    fun `save persists user and findById round-trips all fields`() {
        val user = User(
            id = UserId.random(),
            email = Email.of("alice@example.com"),
            displayName = "Alice",
            roles = setOf(UserRole.OBSERVER, UserRole.RINGER),
            createdAt = Instant.parse("2026-04-18T12:00:00Z"),
        )

        repository.save(user)
        val loaded = repository.findById(user.id)

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.id).isEqualTo(user.id)
        assertThat(loaded.email).isEqualTo(user.email)
        assertThat(loaded.displayName).isEqualTo(user.displayName)
        assertThat(loaded.roles).isEqualTo(user.roles)
        assertThat(loaded.createdAt).isEqualTo(user.createdAt)
    }

    @Test
    fun `findById returns null when user absent`() {
        assertThat(repository.findById(UserId.random())).isNull()
    }

    @Test
    fun `existsByEmail reflects presence`() {
        val email = Email.of("present@example.com")
        assertThat(repository.existsByEmail(email)).isFalse()

        repository.save(
            User(
                id = UserId.random(),
                email = email,
                displayName = "Present",
                roles = setOf(UserRole.OBSERVER),
                createdAt = Instant.now(),
            ),
        )

        assertThat(repository.existsByEmail(email)).isTrue()
    }
}
