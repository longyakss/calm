import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.calm.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.calm.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.0"
    }

    // 签名配置：CI 走环境变量（Actions Secrets）；本地开发读仓库根目录 keystore.properties（gitignored）
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    val storePasswordEnv = System.getenv("CALM_STORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
    val keyAliasEnv = System.getenv("CALM_KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias")
    val keyPasswordEnv = System.getenv("CALM_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("release.keystore")
            storePassword = storePasswordEnv ?: ""
            keyAlias = keyAliasEnv ?: ""
            keyPassword = keyPasswordEnv ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:settings"))
    implementation(project(":data:local"))
    implementation(project(":core:downloader"))
    implementation(project(":core:browser"))
    implementation(project(":core:filemanager"))
    implementation(project(":core:player"))
    implementation(project(":core:reader"))

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
}
