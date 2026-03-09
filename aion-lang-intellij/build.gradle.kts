plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.aion"
version = "0.11.0"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Aion Language"
        version = "0.11.0"
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}
