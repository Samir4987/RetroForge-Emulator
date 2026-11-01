import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.time.Duration
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.aistudio.retroforge.gkqwnx"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isCrunchPngs = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
    ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices {
    missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)

    implementation(libs.converter.moshi)
    implementation(libs.firebase.ai)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.firebase.appcheck.recaptcha)
    implementation(libs.firebase.appcheck.debug)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<Test> {
    failFast = true
    timeout.set(Duration.ofMinutes(2))
    testLogging {
        events("passed", "failed", "skipped")
    }
}

// Nintendo 3DS: stage the separately built Azahar core and its GPL notice.
// Build Azahar with scripts/build-azahar-android.sh first, then run:
// ./gradlew :app:stageAzaharCore :app:assembleDebug
val azaharArtifactDir = rootProject.file(".build/azahar-artifacts/arm64-v8a")
val azaharCoreInput = azaharArtifactDir.resolve("azahar_libretro.so")
val azaharLicenseInput = azaharArtifactDir.resolve("AZAHAR-LICENSE.txt")
val azaharRevisionInput = azaharArtifactDir.resolve("AZAHAR-REVISION.txt")
val azaharSha256Input = azaharArtifactDir.resolve("AZAHAR-SHA256.txt")
val azaharJniLibsDir = layout.buildDirectory.dir("generated/azaharJniLibs").get().asFile
val azaharAssetsDir = layout.buildDirectory.dir("generated/azaharAssets").get().asFile

android.sourceSets.getByName("main").jniLibs.directories.add(azaharJniLibsDir)
android.sourceSets.getByName("main").assets.directories.add(azaharAssetsDir)

tasks.register<Copy>("stageAzaharLicense") {
    from(azaharLicenseInput)
    from(azaharRevisionInput)
    from(azaharSha256Input)
    into(azaharAssetsDir.resolve("licenses/azahar"))
    doFirst {
        require(azaharLicenseInput.isFile && azaharLicenseInput.length() > 0L) {
            "Azahar GPL license notice is missing from the verified artifact."
        }
        require(azaharRevisionInput.readText().trim() == "01623d2fb707317410225c0eced82e3e0c84edc1") {
            "Azahar source revision does not match the pinned integration revision."
        }
        require(azaharCoreInput.isFile && azaharCoreInput.length() > 0L) {
            "Azahar core binary is missing from the verified artifact."
        }
        require(azaharSha256Input.isFile && azaharSha256Input.length() > 0L) {
            "Azahar SHA-256 manifest is missing."
        }
        val expectedHash = azaharSha256Input.readText().trim()
        val actualHash = MessageDigest.getInstance("SHA-256")
            .digest(azaharCoreInput.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        require(expectedHash == actualHash) { "Azahar core SHA-256 verification failed." }
    }
}

tasks.register<Copy>("stageAzaharCore") {
    dependsOn("stageAzaharLicense")
    from(azaharCoreInput)
    into(azaharJniLibsDir.resolve("arm64-v8a"))
    doFirst {
        require(azaharCoreInput.isFile && azaharCoreInput.length() > 0L) {
            "Azahar core not found at ${azaharCoreInput.absolutePath}. " +
                "Run scripts/build-azahar-android.sh before staging."
        }
    }
}
