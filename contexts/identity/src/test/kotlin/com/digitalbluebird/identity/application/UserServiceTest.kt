package com.digitalbluebird.identity.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.digitalbluebird.identity.domain.Email
import com.digitalbluebird.identity.domain.IdentityError
import com.digitalbluebird.identity.domain.User
import com.digitalbluebird.identity.domain.UserRole
import com.digitalbluebird.identity.domain.port.inbound.CreateUserCommand
import com.digitalbluebird.identity.domain.port.outbound.UserRepository
import com.digitalbluebird.shared.domain.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UserServiceTest {

    private val fixedInstant: Instant = Instant.parse("2026-04-18T12:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val users: UserRepository = mockk(relaxed = true)
    private val service = UserService(users, clock)

    @Test
    fun `createUser persists a valid user and stamps createdAt from clock`() {
        every { users.existsByEmail(any()) } returns false
        val saved = slot<User>()
        every { users.save(capture(saved)) } answers { saved.captured }

        val result = service.createUser(
            CreateUserCommand(
                email = "  Alice@Example.COM ",
                displayName = "Alice",
                roles = setOf(UserRole.OBSERVER, UserRole.RINGER),
            ),
        )

        assertThat(result.isRight()).isTrue()
        val user = result.getOrNull()!!
        assertThat(user.email.value).isEqualTo("alice@example.com")
        assertThat(user.displayName).isEqualTo("Alice")
        assertThat(user.roles).isEqualTo(setOf(UserRole.OBSERVER, UserRole.RINGER))
        assertThat(user.createdAt).isEqualTo(fixedInstant)
        verify { users.save(any()) }
    }

    @Test
    fun `createUser defaults roles to OBSERVER when empty`() {
        every { users.existsByEmail(any()) } returns false
        every { users.save(any()) } answers { firstArg() }

        val result = service.createUser(
            CreateUserCommand(email = "bob@example.com", displayName = "Bob", roles = emptySet()),
        )

        assertThat(result.getOrNull()!!.roles).isEqualTo(setOf(UserRole.OBSERVER))
    }

    @Test
    fun `createUser rejects invalid email`() {
        val result = service.createUser(
            CreateUserCommand(email = "not-an-email", displayName = "Alice", roles = setOf(UserRole.OBSERVER)),
        )

        assertThat(result.leftOrNull()!!).isInstanceOf(IdentityError.InvalidEmail::class)
    }

    @Test
    fun `createUser rejects blank displayName`() {
        val result = service.createUser(
            CreateUserCommand(email = "alice@example.com", displayName = "   ", roles = setOf(UserRole.OBSERVER)),
        )

        assertThat(result.leftOrNull()!!).isInstanceOf(IdentityError.InvalidDisplayName::class)
    }

    @Test
    fun `createUser rejects duplicate email`() {
        every { users.existsByEmail(any()) } returns true

        val result = service.createUser(
            CreateUserCommand(email = "dup@example.com", displayName = "Dup", roles = setOf(UserRole.OBSERVER)),
        )

        assertThat(result.leftOrNull()!!).isInstanceOf(IdentityError.EmailAlreadyRegistered::class)
    }

    @Test
    fun `findById returns user when found`() {
        val id = UserId.random()
        val existing = User(id, Email.of("x@y.io"), "X", setOf(UserRole.OBSERVER), fixedInstant)
        every { users.findById(id) } returns existing

        val result = service.findById(id)

        assertThat(result.getOrNull()).isEqualTo(existing)
    }

    @Test
    fun `findById returns UserNotFound when absent`() {
        val id = UserId.random()
        every { users.findById(id) } returns null

        val result = service.findById(id)

        assertThat(result.leftOrNull()!!).isInstanceOf(IdentityError.UserNotFound::class)
    }
}
