plugins {
    id("com.android.application")
}

android {
    namespace = "com.hongguo.core"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hongguo.nativeapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "0.5.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // The repository is built offline; lint's detached artifacts are not cached.
    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependencies {
        implementation("androidx.media3:media3-exoplayer:1.4.1")
        implementation("androidx.media3:media3-ui:1.4.1")
        implementation("androidx.viewpager2:viewpager2:1.1.0")
    }
}
