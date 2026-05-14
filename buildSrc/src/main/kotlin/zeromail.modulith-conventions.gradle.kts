import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("zeromail.spring-boot-conventions")
}

configure<DependencyManagementExtension> {
    // Keep this GA pin in sync with gradle/libs.versions.toml springModulith.
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.6")
    }
}

dependencies {
    "implementation"("org.springframework.modulith:spring-modulith-starter-core")
    "testImplementation"("org.springframework.modulith:spring-modulith-starter-test")
}
