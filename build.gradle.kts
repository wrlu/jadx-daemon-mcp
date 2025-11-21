import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`

    id("com.github.johnrengelman.shadow") version "8.1.1"

	// auto update dependencies with 'useLatestVersions' task
	id("se.patrikerdes.use-latest-versions") version "0.2.18"
	id("com.github.ben-manes.versions") version "0.50.0"
}

dependencies {
	implementation("io.github.skylot:jadx-core:1.5.3")
	implementation("io.github.skylot:jadx-dex-input:1.5.3")
	implementation("io.github.skylot:jadx-java-input:1.5.3")
	implementation("io.github.skylot:jadx-java-convert:1.5.3")
	implementation("io.github.skylot:jadx-smali-input:1.5.3")
	implementation("com.google.code.gson:gson:2.13.2")
	implementation("io.javalin:javalin:6.7.0")
	implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("org.ow2.asm:asm:9.9")
}

repositories {
    mavenCentral()
    maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
    google()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

version = System.getenv("VERSION") ?: "dev"

tasks {
    withType(Test::class) {
        useJUnitPlatform()
    }
}
