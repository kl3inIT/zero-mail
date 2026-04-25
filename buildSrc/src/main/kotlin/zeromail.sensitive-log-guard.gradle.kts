plugins {
    id("zeromail.java-conventions")
}

/**
 * Build-time guard for FND-04: scans production Java sources for Logger calls that reference
 * deny-listed sensitive identifiers (`body`, `bodyText`, `prompt`, `completion`, `rawContent`,
 * `refreshToken`, `accessToken`, `authorizationCode`, `idToken`, `gmailMessagePayload`) or
 * `Sensitive` references outside the approved scrubber package
 * (`com.zeromail.core.privacy`). Fails the build on detection.
 *
 * Two tasks:
 *   - `sensitiveLogGuard` — scans `src/main/java`. Wired into `check`.
 *   - `sensitiveLogGuardNegativeFixture` — scans `src/test/resources/archfixtures` and
 *     INVERTS the result: passes only if the guard reports violations on the canned
 *     unsafe fixture. Proves the guard is not a no-op.
 */
abstract class SensitiveLogGuardTask : org.gradle.api.DefaultTask() {

    @get:org.gradle.api.tasks.InputFiles
    @get:org.gradle.api.tasks.SkipWhenEmpty
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val sources: org.gradle.api.file.ConfigurableFileTree

    @get:org.gradle.api.tasks.Input
    abstract val expectViolations: org.gradle.api.provider.Property<Boolean>

    @get:org.gradle.api.tasks.Input
    abstract val approvedScrubberPackage: org.gradle.api.provider.Property<String>

    @org.gradle.api.tasks.TaskAction
    fun scan() {
        val deny = listOf(
            "body", "bodyText", "prompt", "completion", "rawContent",
            "refreshToken", "accessToken", "authorizationCode", "idToken", "gmailMessagePayload"
        )
        val denyAlt = deny.joinToString("|")
        // Match: <ident>.<level>(...args...) where args reference a deny-listed identifier
        // or `Sensitive` outside the approved package. Multi-line tolerant via DOTALL.
        val loggerCall = Regex(
            """\b(?:log|logger|LOG|LOGGER|[a-zA-Z_]\w*Log\w*)\s*\.\s*(?:info|debug|warn|error|trace)\s*\(([^;]*?)\)\s*;""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        val denyArg = Regex("""\b($denyAlt)\b""")
        val sensitiveType = Regex("""\bSensitive\s*[<(]""")

        val approved = approvedScrubberPackage.get()
        val violations = mutableListOf<String>()

        sources.matching { include("**/*.java") }.forEach { file ->
            val text = file.readText(Charsets.UTF_8)
            // Skip files in the approved scrubber package — they implement the contract.
            val pkgMatch = Regex("""(?m)^\s*package\s+([\w.]+)\s*;""").find(text)
            val pkg = pkgMatch?.groupValues?.get(1) ?: ""
            val skipForSensitiveCheck = pkg.startsWith(approved)

            loggerCall.findAll(text).forEach { m ->
                val args = m.groupValues[1]
                val denyHit = denyArg.find(args)
                val sensitiveHit = if (skipForSensitiveCheck) null else sensitiveType.find(args)
                if (denyHit != null || sensitiveHit != null) {
                    val lineNumber = text.substring(0, m.range.first).count { it == '\n' } + 1
                    val reason = denyHit?.let { "deny-listed identifier `${it.value}`" }
                        ?: "`Sensitive` reference outside `$approved`"
                    violations.add("${file.absolutePath}:$lineNumber → $reason in `${m.value.trim().take(120)}`")
                }
            }
        }

        val expect = expectViolations.get()
        if (expect) {
            if (violations.isEmpty()) {
                throw org.gradle.api.GradleException(
                    "Negative fixture failure: guard found NO violations in fixtures, " +
                            "but the fixtures are designed to be unsafe. The guard is a no-op."
                )
            }
            logger.lifecycle("✓ sensitiveLogGuard negative fixture: detected ${violations.size} expected violation(s)")
        } else {
            if (violations.isNotEmpty()) {
                violations.forEach { logger.error("  ✗ $it") }
                throw org.gradle.api.GradleException(
                    "FND-04 violated: ${violations.size} unsafe Logger call(s) reference deny-listed identifiers. " +
                            "Wrap the value in Sensitive<T> or move the call into `${approved}`."
                )
            }
            logger.lifecycle("✓ sensitiveLogGuard: no unsafe Logger calls in production sources")
        }
    }
}

val approvedPkg = "com.zeromail.core.privacy"

val sensitiveLogGuard = tasks.register<SensitiveLogGuardTask>("sensitiveLogGuard") {
    group = "verification"
    description = "Fails on Logger calls referencing deny-listed sensitive identifiers (FND-04)."
    sources.setDir(layout.projectDirectory.dir("src/main/java"))
    expectViolations.set(false)
    approvedScrubberPackage.set(approvedPkg)
}

tasks.register<SensitiveLogGuardTask>("sensitiveLogGuardNegativeFixture") {
    group = "verification"
    description = "Self-test: asserts the guard reports violations on canned unsafe fixtures."
    sources.setDir(layout.projectDirectory.dir("src/test/resources/archfixtures"))
    expectViolations.set(true)
    approvedScrubberPackage.set(approvedPkg)
}

tasks.named("check") {
    dependsOn(sensitiveLogGuard)
    dependsOn("sensitiveLogGuardNegativeFixture")
}
