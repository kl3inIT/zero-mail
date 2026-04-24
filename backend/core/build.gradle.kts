plugins {
    id("zeromail.spring-boot-conventions")
    id("zeromail.archunit-conventions")
    id("zeromail.modulith-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.liquibase:liquibase-core:5.0.2")
    api("net.logstash.logback:logstash-logback-encoder:8.0")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
}
