plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spring.io/milestone")
    maven("https://repo.spring.io/snapshot")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // --enable-preview: JDK 25 Structured Concurrency (JEP 505) is still preview.
    // Consumed by TenantAwareTaskScope. Revisit when JEP 505 goes GA.
    options.compilerArgs.addAll(listOf("-parameters", "--enable-preview", "-Xlint:deprecation"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun>().configureEach {
    jvmArgs("--enable-preview")
}
