plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.jackson.module.kotlin)

    implementation(project(":shared:domain"))
    implementation(project(":shared:infra"))
    implementation(project(":contexts:identity"))
    implementation(project(":contexts:observations"))
    implementation(project(":contexts:species"))
    implementation(project(":contexts:reserves"))
    implementation(project(":contexts:bookings"))
    implementation(project(":contexts:expeditions"))
    implementation(project(":contexts:surveys"))
    implementation(project(":contexts:permits"))
    implementation(project(":contexts:notifications"))
    implementation(project(":contexts:integrations:ebird"))
    implementation(project(":contexts:integrations:taxonomy"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.modulith.starter.test)
}
