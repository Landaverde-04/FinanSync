plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.grupo4.finansync"
    compileSdk = 35

    // Genera una clase de acceso por cada layout XML (ViewBinding)
    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.grupo4.finansync"
        minSdk = 24
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    //Supabase
    implementation(libs.supabase.postgrest) // Para la base de datos
    implementation(libs.supabase.gotrue)    // Para la autenticación
    implementation(libs.ktor.client.android) // Motor de red

    // ── Módulo 1: Transacciones + Captura (Kevin) ──
    // CameraX (las 4 piezas)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    // OCR
    implementation(libs.mlkit.text.recognition)
    // GPS
    implementation(libs.play.services.location)
    // Lifecycle + Fragment (MVVM en la UI)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.androidx.fragment.ktx)
    // RecyclerView (lista de movimientos)
    implementation(libs.androidx.recyclerview)

    // ── Módulo 5: Reportes, Tema y Accesibilidad (Adam) ──
    // Navigation Component
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    configurations.all {
        resolutionStrategy {
            // Obliga a todo el proyecto a usar las versiones estables compatibles con tu API 35
            force("androidx.core:core:1.15.0")
            force("androidx.core:core-ktx:1.15.0")
            force("androidx.activity:activity:1.9.3")
            force("androidx.activity:activity-ktx:1.9.3")
        }
    }
}
