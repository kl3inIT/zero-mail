plugins {
    base
    id("com.diffplug.spotless") version "8.5.1"
    alias(libs.plugins.cyclonedxBom)
}

cyclonedxBom {
    includeConfigs.set(listOf("runtimeClasspath"))
    skipConfigs.set(listOf("testRuntimeClasspath", "compileClasspath", "testCompileClasspath"))
    outputFormat.set("json")
    outputName.set("sbom")
    destination.set(layout.buildDirectory.dir("reports").get().asFile)
    includeBomSerialNumber.set(true)
    includeLicenseText.set(false)
    schemaVersion.set("1.5")
}

configure(
    listOf(
        project(":backend:core"),
        project(":backend:api"),
        project(":backend:worker"),
    )
) {
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat("1.35.0").aosp()
            formatAnnotations()
            removeUnusedImports()
        }
    }
}

tasks.wrapper {
    gradleVersion = "9.5.0"
    distributionType = Wrapper.DistributionType.BIN
}
