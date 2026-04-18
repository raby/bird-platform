package com.digitalbluebird.identity.adapter.outbound.persistence

import com.digitalbluebird.identity.domain.Email
import com.digitalbluebird.identity.domain.User
import com.digitalbluebird.identity.domain.UserRole
import com.digitalbluebird.identity.domain.port.outbound.UserRepository
import com.digitalbluebird.shared.domain.UserId
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class JdbcUserRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : UserRepository {

    override fun save(user: User): User {
        val params = MapSqlParameterSource()
            .addValue("id", user.id.value)
            .addValue("email", user.email.value)
            .addValue("display_name", user.displayName)
            .addValue("roles", user.roles.map { it.name }.toTypedArray())
            .addValue("created_at", Timestamp.from(user.createdAt))

        jdbc.update(
            """
            INSERT INTO identity.users (id, email, display_name, roles, created_at)
            VALUES (:id, :email, :display_name, :roles, :created_at)
            """.trimIndent(),
            params,
        )
        return user
    }

    override fun findById(id: UserId): User? {
        val params = MapSqlParameterSource("id", id.value)
        return jdbc.query(
            "SELECT id, email, display_name, roles, created_at FROM identity.users WHERE id = :id",
            params,
            ::mapRow,
        ).firstOrNull()
    }

    override fun existsByEmail(email: Email): Boolean {
        val params = MapSqlParameterSource("email", email.value)
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM identity.users WHERE email = :email",
            params,
            Long::class.java,
        ) ?: 0L
        return count > 0
    }

    private fun mapRow(rs: ResultSet, rowNum: Int): User {
        val rolesArray = rs.getArray("roles").array as Array<*>
        val roles = rolesArray.map { UserRole.valueOf(it.toString()) }.toSet()
        return User(
            id = UserId(rs.getObject("id", UUID::class.java)),
            email = Email.of(rs.getString("email")),
            displayName = rs.getString("display_name"),
            roles = roles,
            createdAt = rs.getTimestamp("created_at").toInstant(),
        )
    }
}
