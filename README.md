# ysm-converter

A Java library for decrypting YSM model containers and converting their third-person player
geometry into editable Blockbench projects. Model engines can consume decrypted resources or
the resulting Gson `JsonElement` directly.

This project is a Java port and derivative of OpenYSM's YSMParser. It adds memory-first APIs and
Blockbench conversion, texture inference, animation reference repair and conservative prop
visibility. Original authors retain their attribution. [Original YSMParser](https://github.com/OpenYSM/YSMParser)

## Supported data

| Area | Implemented behavior |
| --- | --- |
| Containers | V1, V2 and V3 detection, decryption, decompression and resource extraction |
| Layouts | Legacy `main.json` and metadata-driven `ysm.json` |
| Geometry | One third-person player geometry, cubes, Bone hierarchy, Pivot, Rotation, inflate, box/per-face UV and locators |
| Textures | Embedded PNG, explicit skins and evidence-based fallback for missing/invalid references |
| Animation | Bone transforms, supported interpolation, Molang expressions and supported effect tracks |
| Bone references | Outliner UUID validation, unambiguous repair and diagnostics for discarded unresolved tracks |
| Props | Conservative default `visibility: false` with inference reasons in warnings |
| Output | Blockbench project format 4.10, `bedrock` model format |

The recorded 906-model parsing corpus exercised internal formats 1, 2, 4, 9, 15, 24, 25, 26 and
28 across V1/V2/V3. Support for future layouts or every model is not guaranteed.

## Requirements and build

The library targets **Java 21** (`--release 21`). Use **JDK 21 or newer**, within the versions
supported by the included Gradle 9.9.1 wrapper. Verification used JDK 25 on Windows.
`zstd-jni` loads a platform-specific native library. Images use JDK ImageIO and TwelveMonkeys.

```powershell
.\gradlew build javadoc
```

On Unix-like systems use `./gradlew` with the same arguments. Output is
`build/libs/ysm-converter-1.0.0.jar` and `build/docs/javadoc/`. Builds may download the Gradle
distribution and dependencies from Maven Central.

## Library dependencies

Publishing is configured for `io.github.toxicity188:ysm-converter:<version>`. No public version
has been published or verified yet. The following `1.0.0` examples describe the intended first
release and will resolve after that release is available. `gradle.properties` is the version source.

Maven Central is the primary repository

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.toxicity188:ysm-converter:1.0.0")
}
```

GitHub Packages is an alternative using the same coordinates and version. Package downloads
require an authorized GitHub token. Supply `githubPackagesUsername` and `githubPackagesPassword`
through local Gradle properties or their `ORG_GRADLE_PROJECT_` environment variables.

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/toxicity188/ysm-converter")
        credentials {
            username = providers.gradleProperty("githubPackagesUsername").orNull
            password = providers.gradleProperty("githubPackagesPassword").orNull
        }
        content { includeGroup("io.github.toxicity188") }
    }
}

dependencies {
    implementation("io.github.toxicity188:ysm-converter:1.0.0")
}
```

JitPack builds the public Git tag using its own namespace. Tags must exactly match the project
version, for example `1.0.0` without a `v` prefix. Its hosted build remains to be verified after
the GitHub repository becomes public.

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
        content { includeGroup("com.github.toxicity188") }
    }
}

dependencies {
    implementation("com.github.toxicity188:ysm-converter:1.0.0")
}
```

For an engine using the local checkout, include it as a Gradle subproject.

In the engine's `settings.gradle.kts`

```kotlin
include(":ysm-converter")
project(":ysm-converter").projectDir = file("../ysm-converter")
```

In the engine's `build.gradle.kts`

```kotlin
plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ysm-converter"))
}
```

Gson is part of the public API and is published as a compile/API dependency through `java-library`.
Direct JAR consumers must supply the resolved runtime dependencies listed in the license inventory.
Shading must preserve ImageIO service registrations and `zstd-jni` native resources.

Local publication can be tested without a signing key

```powershell
.\gradlew publishToMavenLocal -PsigningEnabled=false
```

Release publishing signs the binary, Sources, Javadoc and metadata. GitHub Actions publishes
only after an explicit published release or manual workflow trigger. Configure the `release`
environment with required reviewers for approval. Setup, Secrets and the first-release procedure
are documented in the publishing guide. [Publishing guide](docs/publishing.md)

## Convert a stream in memory

```java
import com.google.gson.JsonElement;
import io.github.toxicity188.ysmconverter.api.YsmConverter;
import io.github.toxicity188.ysmconverter.blockbench.BlockbenchConverter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public final class YsmModels {
    public static JsonElement convert(InputStream input) throws IOException {
        Map<String, byte[]> resources = YsmConverter.decrypt(input);
        return BlockbenchConverter.convert(resources);
    }
}
```

`decrypt` accepts `InputStream`, `byte[]` or `Path`. Memory conversion creates no temporary files.
Caller streams are read from the current position to EOF and remain open on success or failure.
The caller closes them. Input and decoded resources are buffered, so memory use follows model size.

Retain version information and diagnostics through result objects

```java
import io.github.toxicity188.ysmconverter.api.YsmConverter;
import io.github.toxicity188.ysmconverter.blockbench.BlockbenchConverter;
import io.github.toxicity188.ysmconverter.blockbench.BlockbenchResult;

import java.io.IOException;
import java.io.InputStream;

public final class YsmDiagnostics {
    public static BlockbenchResult convert(InputStream input) throws IOException {
        var parsed = YsmConverter.parse(input);
        System.out.println(parsed.version() + "/" + parsed.formatVersion());
        parsed.warnings().forEach(System.err::println);
        var converted = BlockbenchConverter.convertWithWarnings(parsed.resources());
        converted.warnings().forEach(System.err::println);
        return converted;
    }
}
```

`YsmResourceResult.resources()` has an unmodifiable map structure with mutable byte arrays.
`BlockbenchResult.model()` is mutable. Warning lists are immutable snapshots. Parsing can raise
unchecked malformed-input, unsupported-format or integrity exceptions as well as `IOException`.
Conversion reports invalid/unsupported resources through `IOException`.

## Path conveniences

```java
import io.github.toxicity188.ysmconverter.api.YsmConverter;
import io.github.toxicity188.ysmconverter.blockbench.BlockbenchConverter;

import java.io.IOException;
import java.nio.file.Path;

public final class YsmFiles {
    public static void convert(Path source, Path destination) throws IOException {
        var parsed = YsmConverter.parse(source);
        BlockbenchConverter.convertWithWarnings(parsed.resources()).write(destination);
    }

    public static void extractAndConvert(Path source, Path directory, Path destination)
            throws IOException {
        YsmConverter.parse(source, directory);
        BlockbenchConverter.convert(directory).write(destination);
    }
}
```

`parse(Path)` closes its own input stream. Existing `parse(Path, Path)` and `parse(byte[], Path)`
overloads extract resources. `BlockbenchConverter.convert(Path)` reads an extracted directory.
`BlockbenchResult.write(Path)` creates parents and replaces the destination with UTF-8 JSON.
It preserves JSON nulls used for deliberately hidden faces. Custom Gson writers should enable
`serializeNulls()`.

`BlockbenchConverter.repairAnimationReferences(JsonObject)` repairs an existing project's copy.
Valid UUIDs take priority. Stale UUIDs need unambiguous Bone evidence. Unresolvable tracks and
animations emptied by removal can be dropped with diagnostics.

## Restoration behavior and limits

Only third-person player geometry is converted. First-person arm resources, separate projectile
models and TAC animations are excluded. Embedded props remain editable and can start hidden.
The outliner can restore them. Editor visibility is independent of runtime animation scale.
Rules combine hierarchy, animation and spatial evidence. Prefixes alone do not establish roles.
Uncertain candidates remain visible. Unfamiliar rigs, names or model scales can cause classification
errors. Warnings identify the selected subtrees.

Valid explicit texture references take priority. Fallback considers decoded images, logical atlas
and UV constraints, names and paths. Ambiguity can leave faces unassigned with a warning.
Conversion fails when no eligible image can be decoded. The declared providers include no AVIF
decoder, although parsing can preserve AVIF bytes. Auxiliary PBR maps are not applied as color skins.

Conversion requires one player geometry. Unsupported meshes, controllers, easing or fields can
produce warnings or errors. Molang and effect tracks remain editable, while YSM queries, scripts,
sound/particle assets, controls and concurrent animation layers require host runtime support.
Geometry and sampled poses were compared with supplied originals in Blockbench 5.2.2. Source
revisions, rounding, runtime visibility and expression values can change results.
[Conversion rules and recorded validation](docs/blockbench-conversion.md)

## Tests

Core tests use generated data and can run without external models

```powershell
.\gradlew test --tests '*api.*' --tests '*crypto.*' --tests '*compression.*' --tests '*format.*' --tests '*model.*' --tests '*parser.*' --tests '*BlockbenchConverterTest' --tests '*BlockbenchAnimationReferencesTest'
```

Optional compatibility checks use local read-only assets

```powershell
.\gradlew test --tests '*BlockbenchExamplesTest' --tests '*BlockbenchMemoryIntegrationTest'
.\gradlew test --tests 'io.github.toxicity188.ysmconverter.IntegrationTest'
```

Sample checks use `external/examples/` and `external/Yes-Steve-Model-Repo/` when installed.
The extraction integration test writes `build/ysm-parser-tests/reports/parsing-report.json`.
`BlockbenchPropsTest` also uses extracted results when available. The attachment test uses a local
file under `build/`. The existing local `BlockbenchTest` requires the model repository and writes
all conversions to `build/result/`. This optional harness may be absent in a fresh checkout.
A full build runs all tests present locally.

Verification on 2026-11-16 passed `build javadoc` and all 68 locally available tests with no
failures or skips, including the 906-model parsing and full-conversion harnesses. The three Java
examples above compiled with `--release 21` and ran on Asuna. The Gradle subproject example
compiled in a separate engine project. Earlier Blockbench MCP checks covered textures, animations
and default visibility. These results describe the tested corpus and documented snapshots.

## License and distribution

The Java port uses MIT and retains the original YSMParser copyright and permission notice.
`NOTICE` records the port relationship and changes. Dependencies retain their BSD, Apache-2.0
and other applicable terms. [License](LICENSE) · [Attribution](NOTICE) · [Dependency inventory and release obligations](THIRD_PARTY_LICENSES.md)

The standard JAR includes project and runtime legal documents under `META-INF/`. Preserve them
when packaging the library and its dependencies. Models have separate licenses. Decryption and
conversion do not grant redistribution rights over geometry, textures, animations or generated
projects. External model assets and generated review models are excluded from the library release.
