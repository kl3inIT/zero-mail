plugins {
    id("zeromail.spring-boot-conventions")
    id("zeromail.archunit-conventions")
    id("zeromail.modulith-conventions")
    id("zeromail.sensitive-log-guard")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-liquibase")
    api("org.liquibase:liquibase-core:5.0.2")
    api(libs.google.api.services.gmail)
    api("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.starter.model.openai)
    implementation(libs.spring.ai.starter.model.anthropic)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.jtokkit)
    implementation(libs.jsoup)
    runtimeOnly("org.eclipse:yasson")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
}
