import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar

plugins {
    id("java-library")
    id("com.vanniktech.maven.publish") version "0.37.0"
}

val jitPackBuild = providers.environmentVariable("JITPACK").map { it == "true" }.getOrElse(false)
group = if (jitPackBuild) "com.github.toxicity188" else "io.github.toxicity188"

val releaseVersion = version.toString()
val signingEnabled = providers.gradleProperty("signingEnabled").orElse("true").map(String::toBoolean).get()

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
    api("com.google.code.gson:gson:2.15.0")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.15.3")
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.15.3")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.3")
}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)
    if (signingEnabled) signAllPublications()
    coordinates(group.toString(), rootProject.name, releaseVersion)
    configure(JavaLibrary(
        javadocJar = JavadocJar.Javadoc(),
        sourcesJar = SourcesJar.Sources(),
    ))
    pom {
        name.set("YSM Converter")
        description.set("Java library for YSM model decryption and editable Blockbench project conversion.")
        inceptionYear.set("2026")
        url.set("https://github.com/toxicity188/ysm-converter")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/toxicity188/ysm-converter/blob/main/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("toxicity188")
                name.set("toxicity188")
                url.set("https://github.com/toxicity188")
            }
        }
        scm {
            url.set("https://github.com/toxicity188/ysm-converter")
            connection.set("scm:git:https://github.com/toxicity188/ysm-converter.git")
            developerConnection.set("scm:git:ssh://git@github.com/toxicity188/ysm-converter.git")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/toxicity188/ysm-converter")
            credentials {
                username = providers.gradleProperty("githubPackagesUsername")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("githubPackagesPassword")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    from(layout.projectDirectory.dir("src/main/resources/META-INF")) {
        into("META-INF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<PublishToMavenRepository>().configureEach {
    val publicationSigningEnabled = signingEnabled
    doFirst {
        check(publicationSigningEnabled) { "Repository publishing requires signing. Use unsigned publishing only with publishToMavenLocal." }
    }
}

tasks.register("verifyReleaseVersion") {
    group = "verification"
    description = "Checks that the release tag exactly matches the version in gradle.properties."
    val tag = providers.gradleProperty("releaseTag")
    val expectedVersion = releaseVersion
    inputs.property("releaseTag", tag.orElse(""))
    inputs.property("releaseVersion", releaseVersion)
    doLast {
        check(tag.orNull == expectedVersion && !expectedVersion.endsWith("-SNAPSHOT")) {
            "Release tag must exactly match the non-SNAPSHOT project version $expectedVersion."
        }
    }
}
