plugins {
    `java-library`
    `maven-publish`
}

group = "io.github.flashlock"
version = "1.0-SNAPSHOT"

description = "Kahn-style ready-queue for dependency scheduling"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("kahn-queue")
                description.set(project.description)
                url.set("https://github.com/Flashlock/kahn-queue")
            }
        }
    }
    // Add a remote repo via ~/.gradle/gradle.properties, e.g.:
    // mavenPublishUrl=https://oss.sonatype.org/service/local/staging/deploy/maven2/
    // mavenPublishUsername=...
    // mavenPublishPassword=...
    repositories {
        val urlProp = findProperty("mavenPublishUrl") as String?
        if (!urlProp.isNullOrBlank()) {
            maven {
                name = "remote"
                url = uri(urlProp)
                credentials {
                    username = (findProperty("mavenPublishUsername") as String?).orEmpty()
                    password = (findProperty("mavenPublishPassword") as String?).orEmpty()
                }
            }
        }
    }
}
