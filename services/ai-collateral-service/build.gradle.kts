plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    java
}

group = "com.bankapp"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.wrapper {
    gradleVersion = "8.13"
    distributionType = Wrapper.DistributionType.BIN
    networkTimeout = 60_000
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val repoRoot = rootProject.projectDir.parentFile.parentFile
    val envFile = repoRoot.resolve(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    return@forEach
                }
                val separator = trimmed.indexOf('=')
                var key = trimmed.substring(0, separator).trim().removePrefix("\uFEFF")
                var value = trimmed.substring(separator + 1).trim()
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length - 1).trim()
                }
                if (key.isNotEmpty()) {
                    environment(key, value)
                }
            }
    }
}
