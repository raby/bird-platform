package com.digitalbluebird.shared.infra

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class SharedInfraConfiguration {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
