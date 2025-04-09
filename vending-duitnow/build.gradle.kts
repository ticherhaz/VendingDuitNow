import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.ticherhaz.vending_duitnow"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.ksoap)
    implementation ("com.android.volley:volley:1.2.1")
    implementation ("com.squareup.picasso:picasso:2.8")
    implementation ("com.google.code.gson:gson:2.12.1")

    implementation ("com.github.f0ris.sweetalert:library:1.6.2")
    implementation ("com.github.ticherhaz:ComplexView:1.5.3")
}