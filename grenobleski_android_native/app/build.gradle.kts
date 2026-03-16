plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appLovinSdkKey = (project.findProperty("APPLOVIN_SDK_KEY") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: ""

val appLovinBannerUnitId = (project.findProperty("APPLOVIN_BANNER_AD_UNIT_ID") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: ""

val enableMobileAds = ((project.findProperty("ENABLE_MOBILE_ADS") as String?)
    ?.trim()
    ?.lowercase()) in listOf("1", "true", "yes", "on")

android {
    namespace = "fr.grenobleski.nativeapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.grenobleski.nativeapp"
        minSdk = 24
        targetSdk = 34
        // Use CI run number as versionCode when available, fallback to 1 for local builds.
        versionCode = (System.getenv("BUILD_NUMBER") ?: "1").toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"

        buildConfigField("String", "API_BASE_URL", "\"https://www.grenobleski.fr\"")
        buildConfigField("boolean", "ENABLE_MOBILE_ADS", enableMobileAds.toString())
        buildConfigField("String", "APPLOVIN_SDK_KEY", "\"$appLovinSdkKey\"")
        buildConfigField("String", "APPLOVIN_BANNER_AD_UNIT_ID", "\"$appLovinBannerUnitId\"")
        manifestPlaceholders["appLovinSdkKey"] = appLovinSdkKey
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEY_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("com.google.android.material:material:1.12.0")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.applovin:applovin-sdk:13.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
