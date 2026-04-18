package com.digitalbluebird

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic

@SpringBootApplication
@Modulithic(systemName = "bird-platform")
class BirdPlatformApplication

fun main(args: Array<String>) {
    runApplication<BirdPlatformApplication>(*args)
}
