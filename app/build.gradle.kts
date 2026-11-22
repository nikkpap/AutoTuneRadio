plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

// ---- Version -------------------------------------------------------------------------------
// Change appVersionBase in gradle.properties when you start a new version (e.g. 0.24).
// Every commit gets a higher version automatically:
//   commit #57 → 0.24.57  (versionCode 24057) — the same locally and on GitHub
val baseVersion = (project.findProperty("appVersionBase") as String?) ?: "0.23"
// Build number = number of git commits, so Android Studio (Run ▶) and GitHub Actions give the SAME version
// for the same code. Run ▶ therefore installs over an OTA version (no "downgrade" / uninstall), and the next push
// is always higher. BUILD_NUMBER (set by CI to the same commit count) wins when present.
val gitCount = runCatching {
 providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
  .standardOutput.asText.get().trim().toInt()
}.getOrDefault(0)
val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: gitCount
if (buildNumber == 0) logger.warn("AutoTuneRadio: git commit count unavailable — version x.y.0 (open the project from its git clone).")
val versionParts = baseVersion.split('.').map { it.trim().toIntOrNull() ?: 0 }
val major = versionParts.getOrElse(0) { 0 }
val minor = versionParts.getOrElse(1) { 0 }

// ---- Signing -------------------------------------------------------------------------------
// The same key must sign every build, otherwise Android refuses to install an update over the old app.
// The key file is NOT stored in the public repository. Put it next to this file (app/autotune-debug.keystore)
// for local builds; GitHub Actions recreates it from the SIGNING_KEYSTORE_BASE64 secret.
val keystoreFile = file("autotune-debug.keystore")

android {
 namespace = "gr.radiorise.player"
 compileSdk = 35
 defaultConfig {
  applicationId = "gr.radiorise.player"; minSdk = 28; targetSdk = 34
  versionCode = major * 100000 + minor * 1000 + buildNumber   // 0.23.57 → 23057, 1.0.3 → 100003
  versionName = "$baseVersion.$buildNumber"
  buildConfigField("String", "UPDATE_REPO", "\"nikkpap/AutoTuneRadio\"")
 }
 signingConfigs {
  getByName("debug") {
   if (keystoreFile.exists()) {
    storeFile = keystoreFile
    storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "android"
    keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "androiddebugkey"
    keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "android"
   } else logger.warn("AutoTuneRadio: app/autotune-debug.keystore not found — using the default Android debug key (updates will NOT install over the official app).")
  }
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 lint { abortOnError = true; checkReleaseBuilds = false }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-core")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.media3:media3-exoplayer:1.5.1")
 implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.documentfile:documentfile:1.0.1")
 implementation("androidx.work:work-runtime-ktx:2.10.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("io.coil-kt:coil-compose:2.7.0")
 testImplementation("junit:junit:4.13.2")
}
