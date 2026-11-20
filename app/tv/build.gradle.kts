plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("plugin.parcelize")
}

setupCommon()

android {
    namespace = "com.topjohnwu.magisk.tv"

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring(libs.jdk.libs)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.core.ktx)
}
