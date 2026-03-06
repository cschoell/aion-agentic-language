plugins {
    id("aion-lang.java-app")
}

application {
    mainClass.set("com.aion.cli.AionCli")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    if (project.hasProperty("args")) {
        args = (project.property("args") as String).split(" ")
    }
}

dependencies {
    implementation(libs.antlr4.runtime)
    implementation(libs.picocli)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.launcher)
}

