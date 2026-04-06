plugins {
    id("java")
}

group = "com.betts"
version = "1.0-SNAPSHOT"

description = "Immutable DAG + Kahn-style ready-queue for dependency scheduling"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}