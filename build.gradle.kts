plugins {
    base
    id("com.diffplug.spotless") version "8.7.0"
    alias(libs.plugins.cyclonedxBom)
}

// CycloneDX 3.x defaults are fine for our use:
//   - includeConfigs: runtimeClasspath
//   - outputFormat: XML (OSV-Scanner reads XML + JSON)
//   - schemaVersion: 1.5
//   - destination: build/reports/
// Override per-module only if a sub-module needs a custom scope.

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
