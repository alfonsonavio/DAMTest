import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.room)
    alias(libs.plugins.detekt)
}

// Upload key for Google Play, read from keystore.properties (never committed).
// Without that file (e.g. in CI) release builds are simply left unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.navio.damtests"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.navio.damtests"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "2.0.0"
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            // R8: removes unused code and resources and obfuscates the rest
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }

    // Android Lint: any warning fails the build, so the code stays clean.
    // Dependency and target SDK updates are reviewed on purpose, not forced by
    // the build: those checks depend on what is installed on each machine, so
    // the same code could pass locally and fail in CI.
    lint {
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            "OldTargetApi"
        )
        textReport = true
        textOutput = file("build/reports/lint-results.txt")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

// APKs and bundles are named "DAMTest-<version>-<build type>"
base {
    archivesName.set("DAMTest-${android.defaultConfig.versionName}")
}

// detekt: Kotlin static analysis (code smells, complexity, naming) plus the
// ktlint formatting rules. "gradlew detekt --auto-correct" fixes the formatting.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        md.required.set(true)
        txt.required.set(false)
        xml.required.set(false)
        sarif.required.set(false)
    }
}

// Room exports each database version's schema to app/schemas (versioned in
// git) so migrations can be checked against the real previous schemas.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // AndroidX and Material
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)

    // Hilt — dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room — local database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Firebase (versions managed by the BoM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.config)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google sign-in via Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // AI: Gemini, and Groq through its OpenAI-compatible API
    implementation(libs.generativeai)
    implementation(libs.openai.client)

    // Statistics evolution chart
    implementation(libs.mpandroidchart)

    // Networking and serialization
    implementation(libs.okhttp)
    implementation(libs.gson)

    // Static analysis: ktlint formatting rules for detekt
    detektPlugins(libs.detekt.formatting)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}