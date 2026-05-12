# Contributing

## Code formatting

Backend Java is formatted with [google-java-format](https://github.com/google/google-java-format)
in **AOSP style** (4-space indentation, 8-space continuation), enforced by the
[Spotless](https://github.com/diffplug/spotless) Gradle plugin.

- Auto-fix formatting: `./gradlew spotlessApply`
- Verify formatting: `./gradlew spotlessCheck` (also run as part of `./gradlew check`)

CI runs `./gradlew check`, so any unformatted Java file fails the build. A pre-commit
hook (Husky + lint-staged) runs `spotlessApply` on staged `backend/**/*.java` files, so
locally committed changes stay formatted automatically.

### IntelliJ IDEA setup

Install the **google-java-format** IDE plugin (Settings → Plugins → Marketplace →
"google-java-format"), then enable it and set its style to **AOSP**:

- Settings → Other Settings → google-java-format Settings → check **Enable
  google-java-format**, set **Code style** to **Android Open Source Project (AOSP)**.

With that, Reformat Code (Ctrl+Alt+L) produces output identical to Spotless.
