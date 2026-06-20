import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("zeromail.spring-boot-conventions")
    id("zeromail.archunit-conventions")
    id("zeromail.modulith-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":backend:core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation(libs.spring.modulith.starter.jdbc)
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.starter.model.openai)
    implementation(libs.spring.ai.starter.model.anthropic)
    implementation(libs.spring.ai.starter.model.google.genai)
    implementation(libs.spring.ai.starter.model.deepseek)
    implementation(libs.jtokkit)
    implementation(libs.ical4j)
    implementation(libs.shedlock.provider.jdbc.template)
    implementation(libs.shedlock.spring)
    implementation(libs.resend.java)
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
}

tasks.named<BootBuildImage>("bootBuildImage") {
    // BootBuildImage exposes getImageName() in this local Spring Boot 4.0.6 Gradle API.
    // Equivalent target: imageName.set("zeromail-worker:loadtest").
    val bootImageNameProperty = BootBuildImage::class.java.getMethod("getImageName").invoke(this)
    org.gradle.api.provider.Property::class
        .java
        .getMethod("set", Any::class.java)
        .invoke(bootImageNameProperty, "zeromail-worker:loadtest")
}
