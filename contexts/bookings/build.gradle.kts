plugins {
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.arrow.core)
    implementation(libs.jackson.module.kotlin)
    implementation(project(":shared:domain"))
    implementation(project(":shared:infra"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.assertk)
    testImplementation(libs.mockk)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.database.postgresql)
    testImplementation(libs.postgresql)
}
