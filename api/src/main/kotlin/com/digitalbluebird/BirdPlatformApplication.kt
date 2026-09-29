package com.digitalbluebird

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@Modulithic(systemName = "bird-platform")
@EnableScheduling // drives the outbox relay (OutboxPoller) that feeds the read-model projections
class BirdPlatformApplication

fun main(args: Array<String>) {
    runApplication<BirdPlatformApplication>(*args)
}
