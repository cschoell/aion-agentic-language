plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val antlr4Version = "4.13.2"

configurations {
    create("antlr4")
}

val antlr4Cfg = configurations["antlr4"]

dependencies {
    antlr4Cfg("org.antlr:antlr4:$antlr4Version")
}

val generateAntlr = tasks.register<JavaExec>("generateAntlr") {
    val grammarPackage = "com.aion.parser"
    val grammarPackagePath = grammarPackage.replace('.', '/')
    val grammarDir  = file("src/main/antlr4/$grammarPackagePath")
    val outputDir   = layout.buildDirectory.dir("generated/sources/antlr4/main/java/$grammarPackagePath").get().asFile
    inputs.dir(grammarDir)
    outputs.dir(outputDir)

    classpath      = antlr4Cfg
    mainClass.set("org.antlr.v4.Tool")
    args = listOf(
        "-Dlanguage=Java",
        "-package", grammarPackage,
        "-o", outputDir.absolutePath,
        "-visitor",
        "-no-listener",
        grammarDir.resolve("AionLexer.g4").absolutePath,
        grammarDir.resolve("AionParser.g4").absolutePath
    )
    doFirst { outputDir.mkdirs() }
}

sourceSets["main"].java.srcDir(
    generateAntlr.map { layout.buildDirectory.dir("generated/sources/antlr4/main/java").get() }
)

tasks.named("compileJava") { dependsOn(generateAntlr) }

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
