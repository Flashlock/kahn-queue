plugins {
    `java-library`
    `maven-publish`
    signing
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "io.github.flashlock"
// Allow CI/tagged releases to override via `-Pversion=...`
version = (findProperty("version") as String?) ?: "1.0.0"

description = "Lightweight Kahn-based ready queue for dependency-driven scheduling and workflows"

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
                url.set("https://flashlock.github.io/kahn-queue/")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit/")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("flashlock")
                        name.set("Flashlock")
                    }
                }

                scm {
                    url.set("https://github.com/Flashlock/kahn-queue")
                    connection.set("scm:git:https://github.com/Flashlock/kahn-queue.git")
                    developerConnection.set("scm:git:ssh://git@github.com/Flashlock/kahn-queue.git")
                }
            }
        }
    }
}

signing {
    val signingKey = (findProperty("signingKey") as String?) ?: System.getenv("SIGNING_KEY")
    val signingPassword =
        (findProperty("signingPassword") as String?) ?: System.getenv("SIGNING_PASSWORD")

    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}

nexusPublishing {
    repositories {
        sonatype {
            // Recommended: set these via GitHub Actions secrets as ORG_GRADLE_PROJECT_sonatypeUsername / sonatypePassword
            // or via ~/.gradle/gradle.properties for local publishing.
            // Sonatype Central (Publish Portal / OSSRH staging API):
            // https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}
