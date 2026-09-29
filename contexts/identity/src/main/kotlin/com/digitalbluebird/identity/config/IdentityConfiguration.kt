package com.digitalbluebird.identity.config

import com.digitalbluebird.identity.adapter.outbound.persistence.JdbcUserRepository
import com.digitalbluebird.identity.application.UserService
import com.digitalbluebird.identity.domain.port.outbound.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Clock

@Configuration
class IdentityConfiguration {

    @Bean
    fun userRepository(jdbc: NamedParameterJdbcTemplate): UserRepository =
        JdbcUserRepository(jdbc)

    // UserController injects the inbound ports (CreateUserUseCase, FindUserUseCase); both resolve to
    // this single concrete bean. A second, interface-typed bean for the same instance would make that
    // port injection ambiguous (NoUniqueBeanDefinitionException at context startup).
    @Bean
    fun userService(users: UserRepository, clock: Clock): UserService =
        UserService(users, clock)
}
