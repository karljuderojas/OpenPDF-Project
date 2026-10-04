plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.karljuderojas.freepdf"
    // PDFium bindings 2.x require compiling against API 37.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.karljuderojas.freepdf"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("appVersionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("appVersionName").orNull ?: "0.9.0"
    }

    // The release workflow passes the real key through these variables (from repository secrets).
    // Without them the release build is signed with the debug key so it still installs.
    val releaseKeystore = System.getenv("FREEPDF_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("FREEPDF_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FREEPDF_KEY_ALIAS")
                keyPassword = System.getenv("FREEPDF_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric needs merged resources for screenshot tests.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Robolectric's Android 16 sandbox sets raw FileDescriptor fields through JDK internals.
            test.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
            test.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    packaging {
        resources {
            // BouncyCastle (pulled in by PdfBox for signing) ships duplicate metadata.
            excludes += setOf(
                "META-INF/versions/*/OSGI-INF/MANIFEST.MF",
                "META-INF/versions/*/module-info.class",
                "META-INF/{AL2.0,LGPL2.1,LICENSE.md,NOTICE.md}",
            )
        }
    }
}

// PdfBox-Android depends on BouncyCastle 1.72 (jdk15to18 jars) and Robolectric on 1.85 (jdk18on).
// Mixed on one classpath, signing fails with NoSuchFieldError, and 1.72 has known CVEs, so
// every configuration uses the current jdk18on release instead.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        listOf("bcprov", "bcpkix", "bcutil").forEach { artifact ->
            substitute(module("org.bouncycastle:$artifact-jdk15to18"))
                .using(module("org.bouncycastle:$artifact-jdk18on:${libs.versions.bouncycastle.get()}"))
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.pdfium.android)
    implementation(libs.pdfbox.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
