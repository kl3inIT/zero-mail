plugins {
    id("zeromail.java-conventions")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6")
        mavenBom("com.google.cloud:spring-cloud-gcp-dependencies:8.0.2")
    }
}
