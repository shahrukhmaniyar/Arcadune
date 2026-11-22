import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

plugins {
    id("com.android.library")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlinx-serialization")
}

android {
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":lemuroid-touchinput"))

    api(deps.libs.androidx.lifecycle.commonJava8)

    implementation(deps.libs.arch.work.runtime)
    implementation(deps.libs.arch.work.runtimeKtx)
    implementation(deps.libs.androidx.appcompat.appcompat)
    implementation(deps.libs.androidx.leanback.leanbackPreference)
    implementation(deps.libs.androidx.ktx.collection)
    implementation(deps.libs.androidx.ktx.core)
    implementation(deps.libs.androidx.ktx.coreKtx)
    implementation(deps.libs.androidx.fragment.fragment)
    implementation(deps.libs.androidx.fragment.ktx)
    implementation(deps.libs.androidx.activity.activity)
    implementation(deps.libs.androidx.activity.activityKtx)
    implementation(deps.libs.androidx.ktx.coreKtx)
    implementation(deps.libs.androidx.paging.common)
    implementation(deps.libs.androidx.paging.runtime)
    implementation(deps.libs.androidx.room.common)
    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.androidx.room.paging)
    implementation(deps.libs.androidx.documentfile)
    implementation(deps.libs.dagger.android.core)
    implementation(deps.libs.dagger.android.support)
    implementation(deps.libs.okHttp3)
    implementation(deps.libs.okio)
    implementation(deps.libs.retrofit)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)
    implementation(deps.libs.harmony)
    implementation(deps.libs.material)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
    implementation(deps.libs.flowPreferences)

    kapt(deps.libs.androidx.room.compiler)

    testImplementation("junit:junit:4.13.2")
    // Database migration tests run on the PC with Robolectric (an Android stand-in).
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.room:room-testing:${deps.versions.room}")
    testImplementation("androidx.test:core:1.6.1")
}

android {
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        // MigrationTestHelper reads the exported database schemas as test assets.
        getByName("test").assets.srcDir("$projectDir/schemas")
    }
}

android {
    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                argument("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }
    namespace = "com.swordfish.lemuroid.lib"
    kotlinOptions {
        this as KotlinJvmOptions
        jvmTarget = "17"
    }
}
