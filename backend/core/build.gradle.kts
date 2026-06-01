plugins {
    id("zeromail.spring-boot-conventions")
    id("zeromail.archunit-conventions")
    id("zeromail.modulith-conventions")
    id("zeromail.sensitive-log-guard")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("semantic-intent-eval")
    }
}

val aiEvalSourceSet = sourceSets.create("aiEval")
val mainSourceSet = sourceSets.named("main").get()

aiEvalSourceSet.compileClasspath += mainSourceSet.output
aiEvalSourceSet.runtimeClasspath += mainSourceSet.output

configurations.named(aiEvalSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}

configurations.named(aiEvalSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.register<Test>("semanticIntentEval") {
    val testSourceSet = project.extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()["test"]

    group = "verification"
    description = "Offline LLM semantic-intent eval harness (recorded cassettes, no live LLM)."
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    useJUnitPlatform {
        includeTags("semantic-intent-eval")
    }
    shouldRunAfter(tasks.named("test"))
}

tasks.register<Test>("aiEval") {
    group = "verification"
    description = "Offline draft-reply AI eval harness. Deterministic mode runs without live LLM calls."
    testClassesDirs = aiEvalSourceSet.output.classesDirs
    classpath = aiEvalSourceSet.runtimeClasspath
    useJUnitPlatform {
        when {
            providers.gradleProperty("judgeOnly").isPresent -> includeTags("judge")
            providers.gradleProperty("deterministicOnly").isPresent -> excludeTags("judge")
        }
    }
    shouldRunAfter(tasks.named("test"))
}

dependencies {
    api("org.springframework.boot:spring-boot-starter")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-data-redis")
    api("org.springframework.boot:spring-boot-starter-liquibase")
    api("org.springframework.security:spring-security-core")
    api("org.liquibase:liquibase-core:5.0.3")
    api(libs.google.api.services.gmail)
    api("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.modulith.starter.jdbc)
    implementation(libs.spring.ai.starter.model.openai)
    implementation(libs.spring.ai.starter.model.anthropic)
    implementation(libs.spring.ai.starter.model.google.genai)
    implementation(libs.spring.ai.starter.model.deepseek)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.jtokkit)
    implementation(libs.jsoup)
    implementation(libs.google.re2j)
    implementation("com.github.ben-manes.caffeine:caffeine")
    api(libs.jakarta.mail.api)
    api(libs.angus.mail)
    runtimeOnly("org.eclipse:yasson")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
}
