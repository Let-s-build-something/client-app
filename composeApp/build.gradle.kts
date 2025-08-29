@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.BOOLEAN
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.room)

    id("com.google.gms.google-services")

    kotlin("plugin.serialization") version libs.versions.kotlin
    kotlin("native.cocoapods") version libs.versions.kotlin
    id("com.codingfeline.buildkonfig") version libs.versions.buildkonfig
}

val vCode = libs.versions.version.code.get().toInt()
val vName = "${libs.versions.version.name.get()}.$vCode"
val debugHostname = "api.augmy.org"
val releaseHostname = "api.augmy.org"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    cocoapods {
        version = libs.versions.version.name.get()
        summary = "Expressive messenger"
        homepage = "https://augmy.org"

        name = "ComposeApp"

        podfile = project.file("../iosApp/Podfile")
        ios.deploymentTarget = "15.3"
        osx.deploymentTarget = "14.4"

        //pod("GoogleSignIn") { extraOpts += listOf("-compiler-option", "-fmodules") }
        //pod("FirebaseCore") { extraOpts += listOf("-compiler-option", "-fmodules") }
        //pod("FirebaseAuth") { extraOpts += listOf("-compiler-option", "-fmodules") }
        //pod("FirebaseStorage") { extraOpts += listOf("-compiler-option", "-fmodules") }
        pod("FirebaseMessaging") {
            extraOpts += listOf("-compiler-option", "-fmodules")
            linkOnly = true
        }

        framework {
            baseName = "ComposeApp"
            transitiveExport = true
            isStatic = true
            linkerOpts("-ObjC")

            binaryOption("bundleId", "augmy.interactive.com")
            binaryOption("bundleVersion", "$vCode")
        }

        xcodeConfigurationToNativeBuildType["CUSTOM_DEBUG"] = NativeBuildType.DEBUG
        xcodeConfigurationToNativeBuildType["CUSTOM_RELEASE"] = NativeBuildType.RELEASE
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.bundles.kotlin.test)
        }
        androidUnitTest.dependencies {
            implementation(libs.bundles.kotlin.test)
        }
        jvmTest.dependencies {
            implementation(libs.bundles.kotlin.test)
        }
        iosTest.dependencies {
            implementation(libs.bundles.kotlin.test)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(compose.preview)

            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)

            implementation(libs.koin.android)

            implementation(libs.androidx.lifecycle.service)
            implementation(libs.androidx.splashscreen)
            implementation(libs.android.accompanist.permissions)
            implementation(libs.androidx.security.crypto.ktx)

            implementation(libs.coil.gif)
            implementation(libs.android.installreferrer)

            //Credentials
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.auth)
            implementation(libs.google.identity)

            //Firebase
            //implementation(libs.android.firebase.common)
            implementation(libs.android.firebase.messaging)
        }
        nativeMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.bundles.kamel)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.firebase.java.sdk)
            implementation(libs.bundles.kamel)
            implementation(libs.credential.store)
            implementation(libs.java.jwt)
            implementation(libs.logback.classic)
            implementation(libs.oshi.core)

            implementation(libs.ktor.client.java)
            implementation(libs.kotlinx.coroutines.swing)
        }
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(project(":macos"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)

            implementation(libs.compottie.dot)
            implementation(libs.navigation.compose)
            implementation(libs.material3.window.size)
            implementation(libs.compose.file.kit.core)
            implementation(libs.compose.file.kit.dialogs)
            implementation(libs.trixnity.client)
            implementation(libs.trixnity.repository.room)

            implementation(libs.room.runtime)
            //implementation(libs.room.paging)
            implementation(libs.compose.paging.common)
            implementation(libs.sqlite.bundled)

            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.view.model)
            implementation(libs.bundles.settings)
            implementation(libs.datastore.preferences)
            implementation(libs.datastore.preferences.core)

            implementation(libs.kotlin.crypto.sha2)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serialization)
            implementation(libs.bundles.ktor.common)
            implementation(libs.ksoup.korlibs)

            implementation(libs.firebase.gitlive.messaging)

            implementation(libs.coil)
            implementation(libs.coil.svg)
            implementation(libs.coil.compose)
            implementation(libs.coil.compose.core)
            implementation(libs.coil.network.ktor)
            api(libs.compose.webview.multiplatform)
            implementation(libs.media.player.chaintech)

            implementation(libs.lifecycle.runtime)
            implementation(libs.lifecycle.compose)
            implementation(libs.lifecycle.viewmodel)
        }
    }
}

dependencies {
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.activity.ktx)

    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
}

room {
    schemaDirectory("$projectDir/schemas")
}

rootProject.plugins.withType(org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin::class.java) {
    rootProject.the<YarnRootExtension>().yarnLockMismatchReport = YarnLockMismatchReport.WARNING // NONE | FAIL
    rootProject.the<YarnRootExtension>().reportNewYarnLock = false // true
    rootProject.the<YarnRootExtension>().yarnLockAutoReplace = false // true
}

android {
    namespace = "augmy.interactive.com"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "augmy.interactive.com"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = vCode
        versionName = vName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    val keystoreProperties = Properties()
    keystoreProperties.load(FileInputStream(rootProject.file("local.properties")))

    signingConfigs {
        create("dev") {
            keyAlias = "debug"
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
        create("release") {
            keyAlias = "release"
            keyPassword = keystoreProperties["keyPassword"] as String
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = true
            applicationIdSuffix = ".test"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    dependencies {
        debugImplementation(compose.uiTooling)
    }
}

//java.toolchain.languageVersion = JavaLanguageVersion.of(libs.versions.java.get())

compose.desktop {
    application {
        mainClass = "MainKt"

        buildTypes.release.proguard {
            version.set("7.4.0")
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        jvmArgs.add("-Djava.version=17")
        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED") // recommended but not necessary

        if (System.getProperty("os.name").contains("Mac")) {
            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageVersion = vName
            packageName = "Augmy"

            macOS {
                appStore = true
                iconFile.set(project.file("${project.projectDir}/src/nativeMain/resources/drawable/AppIcon.icns"))
            }
            windows {
                modules(
                    "java.instrument",
                    "java.management",
                    "java.naming",
                    "java.sql",
                    "jdk.unsupported",
                    "java.net.http"
                )
                menuGroup = "Augmy Interactive"
                shortcut = true
                iconFile.set(project.file("${project.projectDir}/src/jvmMain/resources/drawable/favicon.ico"))
            }
            linux {
                modules(
                    "java.instrument",
                    "java.management",
                    "java.naming",
                    "java.sql",
                    "jdk.unsupported",
                    "jdk.security.auth",
                    "java.net.http"
                )
                menuGroup = "Augmy Interactive"
                iconFile.set(project.file("${project.projectDir}/src/jvmMain/resources/drawable/favicon.png"))
            }
        }
    }
}

buildkonfig {
    packageName = "augmy.interactive.com"

    val keystoreProperties = Properties()
    keystoreProperties.load(FileInputStream(rootProject.file("local.properties")))

    val flavor = project.findProperty("buildkonfig.flavor")?.toString() ?: "default"

    // this is the production setting
    defaultConfigs {
        buildConfigField(STRING, "CloudWebApiKey", keystoreProperties["cloudWebApiKey"] as String)
        buildConfigField(STRING, "FirebaseProjectId", keystoreProperties["firebaseProjectId"] as String)
        buildConfigField(STRING, "BearerToken", keystoreProperties["bearerToken"] as String)
        buildConfigField(STRING, "GiphyApiKey", keystoreProperties["giphyApiKey"] as String)

        buildConfigField(STRING, "HttpsHostName", releaseHostname)
        buildConfigField(STRING, "AndroidAppId", keystoreProperties["androidReleaseAppId"] as String)
        buildConfigField(STRING, "StorageBucketName", "chat-enrichment.appspot.com")
        buildConfigField(BOOLEAN, "isDevelopment", "false")
    }

    // change the setting just for development
    defaultConfigs("development") {
        buildConfigField(STRING, "HttpsHostName", debugHostname)
        buildConfigField(STRING, "AndroidAppId", keystoreProperties["androidDebugAppId"] as String)
        buildConfigField(BOOLEAN, "isDevelopment", "true")
    }

    targetConfigs(flavor = flavor) {
        all {  }
    }
}

kotlin.sourceSets.all {
    languageSettings.optIn("kotlin.experimental.ExperimentalObjCName")
}

tasks.register("printVersionName") {
    doLast {
        println(vName)
    }
}

// hotfix to faulty room dao generation for Jvm     https://issuetracker.google.com/issues/352482325?pli=1
tasks.register("sanitizeDaoImpls") {
    doLast {
        val baseDir = "build/generated/ksp/jvm/jvmMain/kotlin/database/dao"
        val fileNames = listOf(
            "ConversationMessageDao_Impl.kt",
            "ConversationRoomDao_Impl.kt"
            // Add more DAO impls as needed
        )

        fileNames.forEach { fileName ->
            val file = file("$baseDir/$fileName")

            if (!file.exists()) {
                println("No DAO impl found at $file (skipping)")
                return@forEach
            }

            val original = file.readText()

            // Remove unwanted import
            val noImport = original.replace(
                Regex("""import\s+androidx\.room\.util\.recursiveFetchArrayMap\s*\n"""),
                ""
            )

            // Remove the "if (_map.size > 999)" block
            val patched = noImport.replace(
                Regex("""(?s)(if\s*\(\s*_map\.size\s*>\s*999\s*\)\s*\{\n.*?return\s*\n\s*\})"""),
                ""
            )

            file.writeText(patched)
            println("Patched $fileName — removed recursiveFetchArrayMap logic")
        }
    }
}

// Run after KSP code generation
afterEvaluate {
    tasks.findByName("kspKotlinJvm")?.finalizedBy("sanitizeDaoImpls")
}
