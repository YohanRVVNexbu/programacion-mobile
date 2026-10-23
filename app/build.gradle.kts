import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.gms)
}

// Valores que no se versionan: local.properties (equipo de desarrollo) y keystore.properties (firma).
fun cargar(nombre: String) = Properties().apply {
    rootProject.file(nombre).takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val local = cargar("local.properties")
val claves = cargar("keystore.properties")

// Dirección desde la que el emulador de Android ve la computadora (donde corre el Emulator Suite de Firebase).
val hostEmuladorFirebase = local.getProperty("recordatorios.emuladorHost", "10.0.2.2")

// Commit exacto del código compilado: permite relacionar el reporte de un tester con una entrega concreta.
val commit: String = runCatching {
    ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(rootDir).start()
        .inputStream.bufferedReader().readText().trim().ifEmpty { "sin-git" }
}.getOrDefault("sin-git")

android {
    namespace = "com.yohanvelasquez.recordatorios"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yohanvelasquez.recordatorios"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("recordatorios.versionCode").get().toInt()
        versionName = providers.gradleProperty("recordatorios.versionName").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIT_COMMIT", "\"$commit\"")
    }

    flavorDimensions += "ambiente"
    productFlavors {
        // dev: para desarrollar y probar. Usa el Emulator Suite de Firebase (sin proyecto real, sin datos reales).
        create("dev") {
            dimension = "ambiente"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "Recordatorios (dev)")
            buildConfigField("boolean", "USAR_EMULADOR_FIREBASE", "true")
            buildConfigField("String", "EMULADOR_HOST", "\"$hostEmuladorFirebase\"")
            buildConfigField("boolean", "PERMITE_SIMULACION", "true")
        }
        // prod: versión para testers. Usa el proyecto real de Firebase (app/src/prod/google-services.json).
        create("prod") {
            dimension = "ambiente"
            resValue("string", "app_name", "Recordatorios")
            buildConfigField("boolean", "USAR_EMULADOR_FIREBASE", "false")
            buildConfigField("String", "EMULADOR_HOST", "\"\"")
            buildConfigField("boolean", "PERMITE_SIMULACION", "false")
        }
    }

    signingConfigs {
        // Las credenciales se leen de keystore.properties (fuera del repositorio); nunca se escriben aquí.
        create("release") {
            if (claves.isNotEmpty()) {
                storeFile = file(claves.getProperty("storeFile"))
                storePassword = claves.getProperty("storePassword")
                keyAlias = claves.getProperty("keyAlias")
                keyPassword = claves.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin keystore.properties el release sale sin firmar; el script de entrega lo detecta y se detiene.
            signingConfig = if (claves.isNotEmpty()) signingConfigs.getByName("release") else null
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
    testOptions {
        // Las clases de Firebase llaman a APIs de Android al cargarse; en pruebas JVM devuelven valores por defecto.
        unitTests.isReturnDefaultValues = true
    }
    sourceSets {
        // Esquemas de Room versionados: se usan para probar las migraciones.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
