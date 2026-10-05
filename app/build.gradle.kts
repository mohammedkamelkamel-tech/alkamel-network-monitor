plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "ye.alkamel.networkmonitor"
    compileSdk = 35
    defaultConfig {
        applicationId = "ye.alkamel.networkmonitor"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
