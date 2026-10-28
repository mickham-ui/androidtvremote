plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
//    id("com.google.gms.google-services")
//    id("com.google.firebase.crashlytics")
    id("com.google.protobuf") version "0.9.4"  // Add this line
}

android {
    namespace = "com.out.androidtvremote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.out.androidtvremote"
        minSdk = 24
        targetSdk = 35
        versionCode = 16
        versionName = "1.0.16"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // FIXED: Proper sourceSets configuration for Kotlin DSL
//    sourceSets {
//        getByName("main") {
//            java.srcDirs("src/main/java")
//        }
//    }


    packagingOptions {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/*.kotlin_module")
            excludes.add("META-INF/org/apache/commons/codec/language/bm/gen_approx_greeklatin.txt")
        }
    }

}
// Protobuf configuration
//protobuf {
//    protoc {
//        artifact = "com.google.protobuf:protoc:3.24.4"
//    }
//    generateProtoTasks {
//        all().forEach { task ->
//            task.builtins {
//                maybeCreate("java")  // full java, no lite
//            }
//        }
//    }
//}



dependencies {
    //Coil
    implementation("io.coil-kt:coil:2.7.0")

    //Nano
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-webserver:2.3.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation ("androidx.datastore:datastore-preferences:1.2.0")
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.7")
    implementation ("androidx.preference:preference-ktx:1.2.1")
    //For all screen Size -
    implementation ("com.intuit.sdp:sdp-android:1.1.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    //Glide
    implementation ("com.github.bumptech.glide:glide:5.0.5")
    //Connect SDK
    implementation(project(mapOf("path" to ":ConnectSDK")))
    // Coroutines to make the HTTP requests asynchronous(In the background thread)
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    // Retrofit
    implementation ("com.squareup.retrofit2:retrofit:3.0.0")

    // Okhttp3 for the POST requests
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")

    implementation ("com.squareup.retrofit2:converter-jackson:2.9.0")

    implementation ("com.google.code.gson:gson:2.13.2")

    //Ktor - For api calling
    implementation("io.ktor:ktor-client-core:3.4.0")
    implementation("io.ktor:ktor-client-cio:3.4.0")
    implementation("io.ktor:ktor-server-default-headers:3.4.0")


    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

    

    implementation ("androidx.core:core:1.17.0")
    implementation ("androidx.media:media:1.7.1")
    //Shimmer Effect
    implementation ("com.facebook.shimmer:shimmer:0.5.0")
    //Timber Log
    implementation ("com.jakewharton.timber:timber:5.0.1")



    //Android tv protocol
    implementation("io.reactivex:rxandroid:1.1.0")
    implementation("io.reactivex:rxjava:1.1.0")
    implementation("com.squareup.okhttp3:logging-interceptor:3.12.12")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("com.google.protobuf:protobuf-java:3.24.4")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")

//    implementation("androidx.core:core-splashscreen:1.2.0")



}
