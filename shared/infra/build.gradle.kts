plugins {
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.modulith.starter.core)
    implementation(project(":shared:domain"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.assertk)
    testImplementation(libs.mockk)
}
