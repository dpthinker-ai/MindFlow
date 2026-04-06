import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

val signingProperties = Properties().apply {
    val file = rootProject.file("signing.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

val releaseSigningReady = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { key ->
    signingProperties.getProperty(key)?.isNotBlank() == true
}

fun escaped(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

fun localOrEnv(key: String, envKey: String = key): String =
    (localProperties.getProperty(key) ?: System.getenv(envKey) ?: "").trim()

fun localOrEnvAny(key: String, vararg envKeys: String): String {
    localProperties.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    envKeys.forEach { envKey ->
        System.getenv(envKey)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return ""
}

android {
    namespace = "com.mindflow.app"
    compileSdk = 35

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.mindflow.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "2.0.9"
        resourceConfigurations += listOf("zh", "zh-rCN", "en")
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "AI_API_KEY",
            "\"${escaped(localOrEnvAny("mindflow.ai.apiKey", "MINDFLOW_AI_API_KEY", "ZHIPU_API_KEY", "OPENAI_API_KEY"))}\""
        )
        buildConfigField(
            "String",
            "AI_BASE_URL",
            "\"${escaped(localOrEnvAny("mindflow.ai.baseUrl", "MINDFLOW_AI_BASE_URL", "ZHIPU_BASE_URL", "OPENAI_BASE_URL").ifBlank { "https://open.bigmodel.cn/api/paas/v4" })}\""
        )
        buildConfigField(
            "String",
            "AI_MODEL",
            "\"${escaped(localOrEnvAny("mindflow.ai.model", "MINDFLOW_AI_MODEL", "ZHIPU_MODEL", "OPENAI_MODEL").ifBlank { "glm-4.7" })}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        dex {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    kapt("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
