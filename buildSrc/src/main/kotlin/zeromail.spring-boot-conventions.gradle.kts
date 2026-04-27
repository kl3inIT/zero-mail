import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("zeromail.java-conventions")
    id("io.spring.dependency-management")
}

configure<DependencyManagementExtension> {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6")
        mavenBom("com.google.cloud:spring-cloud-gcp-dependencies:8.0.2")
    }
}

dependencies {
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
