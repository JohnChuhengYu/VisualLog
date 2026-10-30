import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.6.0"
}

kotlin {
    jvmToolchain(18)
}

group = "com.visuallog"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Compose
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Database (Exposed + SQLite)
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("org.xerial:sqlite-jdbc:3.41.2.1")
    
    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.7")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3") // For Dispatchers.Main
}

compose.desktop {
    application {
        mainClass = "com.visuallog.MainKt"
        jvmArgs += listOf(
            "-Xmx2G",
            "-Dskiko.renderApi=METAL"
        )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VisualLog"
            packageVersion = "1.0.3"
            modules("java.sql", "java.desktop")

            macOS {
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))
                bundleID = "com.visuallog.app"
            }
            windows {
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
                menuGroup = "VisualLog"
                upgradeUuid = "8b14e365-5c12-4d2b-92ea-2c8b74681a9f"
            }
            linux {
                iconFile.set(project.file("src/main/resources/icons/icon.png"))
            }
        }
        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
    }
}
