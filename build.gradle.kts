plugins {
    id("java")
}

group = "io.github.toxicity188.ysmconverter"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.1.0")
    testCompileOnly("org.jetbrains:annotations:26.1.0")

    implementation("com.github.luben:zstd-jni:1.5.7-25")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.4")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
