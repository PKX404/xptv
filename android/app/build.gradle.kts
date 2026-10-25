plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
 namespace = "com.pkx404.xparty"
 compileSdk = 35
 buildToolsVersion = "35.0.0"
 defaultConfig { applicationId = "com.pkx404.xparty"; minSdk = 26; targetSdk = 35; versionCode = 12; versionName = "1.2.0" }
 buildFeatures { buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8; isCoreLibraryDesugaringEnabled = true }
 kotlinOptions { jvmTarget = "1.8" }
 packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST", "META-INF/versions/9/OSGI-INF/MANIFEST.MF") }
 signingConfigs {
  create("ciRelease") {
   System.getenv("XPARTY_KEYSTORE")?.let { storeFile = file(it) }
   storePassword = System.getenv("XPARTY_STORE_PASS")
   keyAlias = "xparty"; keyPassword = System.getenv("XPARTY_STORE_PASS")
  }
 }
 buildTypes { getByName("release") { isMinifyEnabled = false; if (System.getenv("XPARTY_KEYSTORE") != null) signingConfig = signingConfigs.getByName("ciRelease") } }
}

dependencies {
 implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
 implementation("com.google.android.exoplayer:exoplayer-dash:2.19.1")
 implementation("com.google.android.exoplayer:exoplayer-hls:2.19.1")
 implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")
 implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.github.bumptech.glide:glide:4.16.0")
 implementation("com.google.android.material:material:1.12.0")
 implementation("androidx.recyclerview:recyclerview:1.3.2")
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
 coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
 testImplementation("junit:junit:4.13.2")
}

