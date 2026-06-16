import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    id("zeromail.java-conventions")
    id("io.spring.dependency-management")
}

// Override the Boot-managed OpenTelemetry version to patch CVE-2026-45292
// (unbounded memory in W3C Baggage propagation). The fix lands in 1.62.0. OTel
// keeps API/ABI stability across 1.x minors, so the io.spring.dependency-management
// property override is safe and stays as a floor even if Boot already manages >=1.62.0.
extra["opentelemetry.version"] = "1.62.0"

configure<DependencyManagementExtension> {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
    }
}

dependencies {
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
