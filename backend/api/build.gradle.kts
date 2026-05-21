import org.gradle.api.tasks.Exec
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    id("zeromail.spring-boot-conventions")
    id("zeromail.archunit-conventions")
    id("zeromail.modulith-conventions")
    id("org.springframework.boot")
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
}

dependencies {
    implementation(project(":backend:core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.security:spring-security-webauthn")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.session:spring-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation(libs.google.auth.library.oauth2.http)
    compileOnly(platform(libs.spring.ai.bom))
    compileOnly("org.springframework.ai:spring-ai-model")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

tasks.named<BootBuildImage>("bootBuildImage") {
    // BootBuildImage exposes getImageName() in this local Spring Boot 4.0.6 Gradle API.
    // Equivalent target: imageName.set("zeromail-api:loadtest").
    val bootImageNameProperty = BootBuildImage::class.java.getMethod("getImageName").invoke(this)
    org.gradle.api.provider.Property::class
        .java
        .getMethod("set", Any::class.java)
        .invoke(bootImageNameProperty, "zeromail-api:loadtest")
}

tasks.register<Exec>("loadtestVerify") {
    group = "verification"
    description =
        "Runs the three loadtest invariant assertions via loadtest/scripts/loadtest-verify.sh (psql shell-out per codex HIGH-8). Assumes worker queue is drained (call loadtest/scripts/wait-for-worker-drain.sh first per MED-3)."
    workingDir = rootProject.projectDir
    val gitBash = file("C:/Program Files/Git/bin/bash.exe")
    val bashExecutable =
        if (System.getProperty("os.name").lowercase().contains("windows") && gitBash.isFile) {
            gitBash.absolutePath
        } else {
            "bash"
        }
    commandLine(bashExecutable, "loadtest/scripts/loadtest-verify.sh")
}

openApi {
    // Phase 1.2.1 D-D5 + W3 closure: emit the full /v3/api-docs spec at
    // apps/web/openapi/openapi.json so the frontend codegen step does not
    // need a live backend. The plugin starts Spring context, writes the spec,
    // and shuts down — hermetic and Windows-compatible (no PID files / kill).
    //
    // Bind to an unusual port (59280) so the emit task never collides with a
    // dev `bootRun` (8080) or Windows Hyper-V's common excluded port ranges.
    customBootRun {
        // Pass the docker-compose file as an absolute path so the working
        // directory of the forked JVM does not matter (setting workingDir
        // makes Gradle 9 try to hash the entire repo root as a task input,
        // which fails on locked .gradle/ files). The path is resolved here
        // at configuration time from the rootProject layout.
        args.set(
            listOf(
                "--server.port=59280",
                "--spring.docker.compose.file=" + rootProject.file("docker-compose.yml").absolutePath,
                // Dummy OAuth2 + crypto credentials so the boot succeeds without real env
                // vars. The spec emit only needs the controllers/DTOs introspected — runtime
                // OAuth and crypto are never exercised. These literals are NEVER committed
                // anywhere except this build script and never reach a deployed environment.
                // Phase 01.5: single bundled google registration (google-gmail deleted).
                "--spring.security.oauth2.client.registration.google.client-id=openapi-emit",
                "--spring.security.oauth2.client.registration.google.client-secret=openapi-emit",
                // Provide a 32-byte AES-GCM key (base64) so RefreshTokenCipher beans initialize.
                "--zero-mail.crypto.refresh-token-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "--zero-mail.api.gmail.pubsub.push-audience-url=https://openapi.invalid/internal/pubsub/gmail",
                "--zero-mail.api.gmail.pubsub.sa-principal-email=pubsub-openapi@openapi.invalid",
                "--zero-mail.api.gmail.pubsub.oidc-certificates-url=https://www.googleapis.com/oauth2/v3/certs",
                "--zero-mail.billing.sepay.webhook-api-key=openapi-emit",
                "--zero-mail.billing.payment-account.bank-code=VCB",
                "--zero-mail.billing.payment-account.bank-name=Vietcombank",
                "--zero-mail.billing.payment-account.account-number=0000000000",
                "--zero-mail.billing.payment-account.account-name=ZERO MAIL",
                "--zero-mail.billing.vnd-per-credit=1000",
                "--zero-mail.billing.max-pending-intents-per-tenant=5",
                "--zero-mail.billing.intent-expiry=PT24H",
                "--zero-mail.llm.platform.api-key=openapi-emit",
                "--spring.ai.google.genai.api-key=openapi-emit",
                "--spring.ai.deepseek.api-key=openapi-emit"
            )
        )
    }
    apiDocsUrl.set("http://localhost:59280/v3/api-docs")
    outputDir.set(rootProject.file("apps/web/openapi"))
    outputFileName.set("openapi.json")
    waitTimeInSeconds.set(180)
}
