plugins {
    id("zeromail.spring-boot-conventions")
    id("zeromail.archunit-conventions")
    id("zeromail.modulith-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":backend:core"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
}
