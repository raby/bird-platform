plugins {
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(libs.spring.boot.starter)
    implementation(project(":shared:domain"))
    implementation(project(":shared:infra"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.assertk)
    testImplementation(libs.mockk)
}
