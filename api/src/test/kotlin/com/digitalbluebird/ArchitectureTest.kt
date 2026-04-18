package com.digitalbluebird

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

class ArchitectureTest {

    private val productionClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.digitalbluebird")

    @Test
    fun `domain layers must not depend on adapter layers`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..adapter..")
            .check(productionClasses)
    }

    @Test
    fun `domain layers must not depend on Spring Framework`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .check(productionClasses)
    }

    @Test
    fun `domain layers must not depend on Jakarta EE`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("jakarta..")
            .check(productionClasses)
    }

    @Test
    fun `domain layers must not depend on jOOQ or JDBC`() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.jooq..", "java.sql..")
            .check(productionClasses)
    }
}
