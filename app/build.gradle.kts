import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.kapt") }
android {
 namespace="dev.attention.app"
 compileSdk=35
 buildToolsVersion="35.0.0"
 defaultConfig { applicationId="dev.attention.app"; minSdk=26; targetSdk=35; versionCode=3; versionName="1.2.0"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 val signingProps=rootProject.file("signing/local.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
 val storeFileValue=(System.getenv("FOCUS_KEYSTORE")?.takeIf { it.isNotBlank() }?.let { file(it) } ?: rootProject.file("signing/personal.jks")).takeIf { it.exists() }
 val storePasswordValue=signingProps?.getProperty("storePassword") ?: System.getenv("FOCUS_KEYSTORE_PASSWORD")
 val keyPasswordValue=signingProps?.getProperty("keyPassword") ?: System.getenv("FOCUS_KEY_PASSWORD") ?: storePasswordValue
 val hasPersonalSigning=storeFileValue!=null && !storePasswordValue.isNullOrBlank()
 if(!hasPersonalSigning) logger.warn("FOCUS: personal signing key not found (signing/personal.jks + signing/local.properties or FOCUS_KEYSTORE* env). Release APK will be UNSIGNED.")
 signingConfigs { create("personal") { if(hasPersonalSigning){ storeFile=storeFileValue;storePassword=storePasswordValue;keyAlias="attention";keyPassword=keyPasswordValue } } }
 buildTypes { release { isMinifyEnabled=true;isShrinkResources=true;if(hasPersonalSigning) signingConfig=signingConfigs.getByName("personal");proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 buildFeatures { compose=true }
 testOptions { unitTests.isIncludeAndroidResources=true }
 packaging { resources.excludes+="/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 kapt("androidx.room:room-compiler:2.6.1")
 implementation("androidx.datastore:datastore-preferences:1.1.1")
 implementation("androidx.core:core-ktx:1.15.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.14.1")
 testImplementation("androidx.test:core:1.6.1")
 testImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
// Allow JVM integration tests to use the caller's configured build proxy.
tasks.withType<Test>().configureEach {
 listOf("http.proxyHost","http.proxyPort","https.proxyHost","https.proxyPort").forEach { key -> System.getProperty(key)?.let { systemProperty(key,it) } }
 systemProperty("robolectric.dependency.repo.url","https://repo.maven.apache.org/maven2")
}
