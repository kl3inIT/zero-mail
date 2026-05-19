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

val utf8RuntimeJvmArgs =
    listOf("--enable-preview", "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // --enable-preview: JDK 25 Structured Concurrency (JEP 505) is still preview.
    // Consumed by TenantAwareTaskScope. Revisit when JEP 505 goes GA.
    options.compilerArgs.addAll(listOf("-parameters", "--enable-preview", "-Xlint:deprecation"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(utf8RuntimeJvmArgs)
    // Phase 7 expanded the Spring context (Spring AI + 24 chat tool handlers + chat module beans),
    // pushing the default test JVM into native-memory OOM during bean creation
    // (EnumSet.java:118 in Spring's ConfigurationClassPostProcessor). Raise heap, metaspace,
    // and code-cache ceilings so full @SpringBootTest contexts can load reliably on CI runners.
    maxHeapSize = "2g"
    jvmArgs("-XX:MaxMetaspaceSize=512m", "-XX:ReservedCodeCacheSize=256m")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(utf8RuntimeJvmArgs)
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun>().configureEach {
    jvmArgs(utf8RuntimeJvmArgs)
}
