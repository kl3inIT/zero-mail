plugins {
    base
    id("com.diffplug.spotless") version "8.4.0"
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
    gradleVersion = "9.4.1"
    distributionType = Wrapper.DistributionType.BIN
}
