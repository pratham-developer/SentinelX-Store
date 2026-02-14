plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.10")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")
        force("org.jetbrains.kotlin:kotlin-reflect:1.9.10")
    }
}

android {
    namespace = "com.pratham.sentinelxstore"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pratham.sentinelxstore"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            // General Excludes
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"

            // Netty & Web3j Specifics
            excludes += "META-INF/io.netty.versions.properties"

            // Jackson & AWS SDK Specifics
            excludes += "META-INF/FastDoubleParser-LICENSE"
            excludes += "META-INF/FastDoubleParser-NOTICE"

            // Bouncy Castle & Java 9+ Module conflicts
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "module-info.class"
        }
    }
}

dependencies {

    // CRITICAL FIX: Force Kotlin stdlib to match compiler version
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.10"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Web3j for Blockchain Interaction
    implementation("org.web3j:core:5.0.2")

    // FIXED: Coroutines compatible with Kotlin 1.9.10
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Material Design
    implementation("com.google.android.material:material:1.13.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // FIXED: OkHttp compatible with Kotlin 1.9.10
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.activity:activity-ktx:1.9.0")
}