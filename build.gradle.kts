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

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("com.github.luben:zstd-jni:1.5.7-25")
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.15.3")
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.15.3")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.3")
}

tasks.test {
    useJUnitPlatform()
}
