plugins {
    id("zeromail.spring-boot-conventions")
}

dependencyManagement {
    // Pin matches libs.versions.toml springModulith; snapshot is the only Boot-4-compatible line
    // as of 2026-04-24. When 2.0.0-M1+ ships, update both here and in the catalog in lockstep.
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:2.0.7-SNAPSHOT")
    }
}

dependencies {
    "implementation"("org.springframework.modulith:spring-modulith-starter-core")
    "testImplementation"("org.springframework.modulith:spring-modulith-starter-test")
}
