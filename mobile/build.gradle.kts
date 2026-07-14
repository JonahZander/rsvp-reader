plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jonahzander.rsvpreader.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jonahzander.rsvpreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
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
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.documentfile)

    implementation(libs.jsoup)
    implementation(libs.epub4j.core) {
        exclude(group = "net.sf.kxml", module = "kxml2")
        // org.xmlpull.v1.XmlPullParser is already provided by the Android
        // platform; bundling this artifact's copy as a program class makes
        // R8 fail with "Library class android.content.res.XmlResourceParser
        // implements program class org.xmlpull.v1.XmlPullParser".
        exclude(group = "xmlpull", module = "xmlpull")
    }

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    // epub4j needs an XmlPullParser implementation; Android provides one, the JVM test runtime does not
    testImplementation(libs.kxml2)
}
