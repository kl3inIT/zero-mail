plugins {
    `java-library`
    jacoco
}

jacoco {
    // 0.8.14 published Dec 2024 added JDK 25 class-file reader support, but the
    // runtime agent still cannot instrument classes compiled with --enable-preview
    // (JEP 505 markers fail at Instrumenter#instrument). Coverage stays opt-in
    // until the agent catches up — see comment on jacocoTestReport below.
    toolVersion = "0.8.14"
}

tasks.withType<JacocoReport>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    // Exclude generated, DTOs (records), and Spring config glue from coverage stats.
                    exclude(
                        "**/dto/**",
                        "**/config/**",
                        "**/generated/**",
                        "**/*Application.class",
                        "**/*Configuration*.class",
                    )
                }
            },
        ),
    )
}

tasks.withType<Test>().configureEach {
    // Disable the JaCoCo Java agent on tests until JaCoCo officially supports
    // --enable-preview JDK 25 bytecode. With the agent disabled, the test JVM
    // boots cleanly and `gradle check` no longer fails on instrumentation.
    extensions.findByType<JacocoTaskExtension>()?.isEnabled = false
}

tasks.named("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    onlyIf {
        // Skip cleanly when no execution data is present (the agent is disabled
        // above — re-enable per-module to generate a real report locally).
        fileTree(layout.buildDirectory.dir("jacoco")).matching { include("**/*.exec") }.any()
    }
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
    listOf(
        "--enable-preview",
        "-Dfile.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8",
        "-Duser.timezone=UTC",
    )

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
