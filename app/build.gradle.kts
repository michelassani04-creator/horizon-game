plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Adresse du serveur HORIZON intégrée à l'application : propriété Gradle ou variable d'environnement HORIZON_API_URL.
val horizonApiUrl: String = listOf(project.findProperty("HORIZON_API_URL") as String?, System.getenv("HORIZON_API_URL"))
    .firstOrNull { !it.isNullOrBlank() }?.trim()?.trimEnd('/') ?: ""

android {
    namespace = "com.horizon.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.horizon.game"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "0.5.0"
        buildConfigField("String", "API_URL", "\"$horizonApiUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
