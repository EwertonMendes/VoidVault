plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.voidvault"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val hytaleHome: String? = findProperty("hytale.home_path") as String?

repositories {
    mavenCentral()
}

dependencies {
    // Configure this path in gradle.properties. See gradle.properties.example.
    if (!hytaleHome.isNullOrBlank()) {
        compileOnly(fileTree(hytaleHome!!) {
            include("**/*.jar")
        })
    }

    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.mongodb:bson:5.2.1")
    implementation("org.slf4j:slf4j-nop:2.0.16")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.shadowJar {
    archiveBaseName.set("VoidVault")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
