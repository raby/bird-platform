package com.digitalbluebird.identity.config

import com.digitalbluebird.identity.adapter.outbound.persistence.JdbcUserRepository
import com.digitalbluebird.identity.application.UserService
import com.digitalbluebird.identity.domain.port.inbound.CreateUserUseCase
import com.digitalbluebird.identity.domain.port.inbound.FindUserUseCase
import com.digitalbluebird.identity.domain.port.outbound.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock
import javax.sql.DataSource

@Configuration
class IdentityConfiguration {

    @Bean
    fun identityJdbcTemplate(dataSource: DataSource): NamedParameterJdbcTemplate =
        NamedParameterJdbcTemplate(dataSource)

    @Bean
    fun userRepository(jdbc: NamedParameterJdbcTemplate): UserRepository =
        JdbcUserRepository(jdbc)

    @Bean
    fun userService(users: UserRepository, clock: Clock): UserService =
        UserService(users, clock)

    @Bean
    fun createUserUseCase(userService: UserService): CreateUserUseCase = userService

    @Bean
    fun findUserUseCase(userService: UserService): FindUserUseCase = userService
}
