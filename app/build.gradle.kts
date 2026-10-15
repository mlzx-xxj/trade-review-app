plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.tradereview.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tradereview.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 21
        versionName = "2.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    sourceSets["androidTest"].java.srcDir("src/testShared/kotlin")
    sourceSets["test"].java.srcDir("src/testShared/kotlin")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { animationsDisabled = true }
    buildTypes {
        getByName("release") { isDebuggable = false; isMinifyEnabled = false }
        create("rc") {
            initWith(getByName("release"))
            applicationIdSuffix = ".rc.v200"
            versionNameSuffix = "-rc.2"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            resValue("string", "app_name", "交易复盘 RC")
        }
    }
    // Only the formal Release can use the existing private identity. RC never reuses it.
    val existingKey = providers.environmentVariable("TRADE_REVIEW_KEYSTORE_PATH").orNull
    if (existingKey != null) {
        val fixed = signingConfigs.create("existingV03") {
            storeFile = file(existingKey)
            storePassword = providers.environmentVariable("TRADE_REVIEW_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("TRADE_REVIEW_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("TRADE_REVIEW_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = fixed
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("com.google.code.gson:gson:2.12.1")
    // Bundled Chinese + Latin recognizer. No Play Services model download dependency.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Optional IDE inspector, not needed in the delivered Debug APK. Models/ABIs remain complete.
    if(providers.gradleProperty("enableComposeTooling").orNull=="true")debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
