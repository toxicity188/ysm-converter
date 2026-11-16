# Third-party licenses and distribution policy

Reviewed against `build.gradle.kts`, Gradle's resolved `runtimeClasspath` and
`testRuntimeClasspath`, cached source/binary JARs and the pinned YSMParser submodule on
2026-11-16. Versions below describe this checkout. Recheck this inventory when versions,
packaging or copied source change.

## Project and ported code

The Java port uses MIT. The root `LICENSE` retains the upstream copyright line exactly,
including its brackets, and adds a separate notice for Java contributors. `NOTICE` states
the relationship and modifications. MIT requires retention of the copyright and permission
notice in copies or substantial portions. It permits use, modification, commercial distribution
and sublicensing subject to that condition. MIT itself does not require a modification notice
or publication of source. This project records its port changes voluntarily and preserves the
additional terms of applicable components.

The exact upstream text is copied from submodule revision
`86c48922ecae79c4e9d16bffed1e8becbe849f98`. The upstream file has not been edited.
[Pinned YSMParser license](https://github.com/OpenYSM/YSMParser/blob/86c48922ecae79c4e9d16bffed1e8becbe849f98/LICENSE.txt)

Canonical runtime/provenance license texts are in `src/main/resources/META-INF/licenses/`.
The standard Java resource processing includes them in `META-INF/licenses/` in the ordinary JAR.

| Component or lineage | Terms | Retained file in that directory |
| --- | --- | --- |
| OpenYSM YSMParser, Java parsing/crypto/geometry port | MIT | `YSMParser-MIT.txt` |
| YSM-modified CityHash64, Google, Inc. | MIT | `CityHash-MIT.txt` |
| XChaCha20, Bryan Hawkins | BSD-3-Clause | `xchacha20-BSD-3-Clause.txt` |
| XChaCha20's ChaCha/libsodium lineage, Daniel J. Bernstein and Frank Denis | Public-domain attribution and ISC terms | `xchacha20-NOTICE.txt` |
| Reference Zstandard implementation/custom YSM handling | BSD-3-Clause | `YSMParser-zstd-BSD-3-Clause.txt` |
| Reference repository AES, MD5, cpp-base64 and zlib | MIT, MIT, Zlib-style, Zlib | `AES-MIT.txt`, `md5-MIT.txt`, `cpp-base64-Zlib.txt`, `zlib.txt` |

The last row preserves the reference repository's bundled dependency notices. Java uses JCA/JDK
AES, MD5, Base64 and zlib APIs. These C++ libraries are not runtime dependencies of the Java JAR.
The original submodule retains its complete licenses for its own source distribution. Its other
optional tools or bundled components are outside the Java artifact inventory.

BSD redistribution requires the copyright, conditions and disclaimer in source and/or binary
distribution materials as specified by each text. BSD-3-Clause also restricts endorsement using
the named authors. ISC requires its copyright and permission notice. Zlib-style notices restrict
misrepresentation of origin, require altered source versions to be marked and require retention
of source notices. Keep the original texts and port attribution when redistributing applicable work.

## Runtime dependencies

| Resolved coordinates | Terms | Retained files |
| --- | --- | --- |
| `com.github.luben:zstd-jni:1.5.7-25` | BSD-2-Clause | `zstd-jni-BSD-2-Clause.txt` |
| Native Zstandard and its adapted xxHash included by zstd-jni | BSD-3-Clause selected from upstream's BSD/GPL alternatives | `zstd-BSD-3-Clause.txt` and attribution in `NOTICE` |
| `com.google.code.gson:gson:2.15.0` | Apache-2.0 | `Apache-2.0.txt`, `Google-source-notices.txt` |
| `com.google.errorprone:error_prone_annotations:2.50.0` (transitive) | Apache-2.0 | Same Apache/Google files |
| `com.twelvemonkeys.imageio:imageio-core:3.15.3` | BSD-3-Clause | `TwelveMonkeys-BSD-3-Clause.txt`, `TwelveMonkeys-source-notices.txt` |
| `com.twelvemonkeys.imageio:imageio-jpeg:3.15.3` | BSD-3-Clause | Same TwelveMonkeys files |
| `com.twelvemonkeys.imageio:imageio-webp:3.15.3` | BSD-3-Clause | Same TwelveMonkeys files |
| `com.twelvemonkeys.imageio:imageio-metadata:3.15.3` (transitive) | BSD-3-Clause | Same TwelveMonkeys files |
| `com.twelvemonkeys.common:common-lang:3.15.3` (transitive) | BSD-3-Clause | Same TwelveMonkeys files |
| `com.twelvemonkeys.common:common-io:3.15.3` (transitive) | BSD-3-Clause | Same TwelveMonkeys files |
| `com.twelvemonkeys.common:common-image:3.15.3` (transitive) | BSD-3-Clause | Same TwelveMonkeys files |

Runtime source JAR headers were inspected because some binary JARs have no license/notice entry.
The `*-source-notices.txt` files reproduce the distinct leading notices from the resolved source
JARs, including contributor copyright lines and permission/disclaimer text. Source entry labels
are provenance information. They do not declare modifications to those dependencies.

Apache-2.0 requires a license copy, retained applicable source attribution and any applicable
upstream NOTICE attribution. Modified files must carry prominent change notices if you modify
Apache-covered source. The current project links unmodified dependency artifacts. Preserve
dependency notices when repackaging. The Apache text also contains patent and trademark terms.

Zstd-jni's wrapper license and bundled native Zstandard license are distinct. This distribution
uses the native BSD option, so using zstd-jni does not require selecting the alternative GPL terms.
The license does not guarantee a native binary for every platform.
[zstd-jni license](https://github.com/luben/zstd-jni/blob/v1.5.7-25/LICENSE),
[bundled native license](https://github.com/luben/zstd-jni/blob/v1.5.7-25/src/main/native/LICENSE),
[Gson license](https://github.com/google/gson/blob/main/LICENSE),
[TwelveMonkeys license](https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.15.3/LICENSE.txt)

## Compile, test and build tools

| Component | Scope | Terms and location |
| --- | --- | --- |
| `org.jetbrains:annotations:26.1.0` | Main/test compile-only | Apache-2.0, `JetBrains-source-notices.txt` and `Apache-2.0.txt` |
| JUnit Jupiter API/engine/params and JUnit Platform commons/engine/launcher `6.1.3`, JUnit BOM | Tests | EPL-2.0, `docs/licenses/testing/JUnit-EPL-2.0.txt` and `JUnit-NOTICE.txt` |
| FastCSV code included in JUnit Jupiter Params | Tests | MIT, `docs/licenses/testing/FastCSV-MIT.txt` |
| `org.opentest4j:opentest4j:1.3.0` | Tests, transitive | Apache-2.0, `docs/licenses/testing/OpenTest4J-Apache-2.0.txt` |
| Gradle wrapper scripts and wrapper JAR, Gradle `9.9.1` | Build | Apache-2.0, existing script notices and retained `Apache-2.0.txt` |

The library JAR contains no JUnit classes. Redistribution or modification of test tooling is
subject to its own terms, including EPL source-distribution obligations where applicable.
The downloaded Gradle distribution and JDK have their own complete notices. Preserve those if
redistributing those products. Building with them does not incorporate the entire distributions
into the library. Kotlin DSL build files do not add Kotlin runtime code to this Java artifact.

## Release procedure

1. Run the documented build and tests. Review the runtime dependency tree after version changes.
2. Keep root `LICENSE`, `NOTICE` and this inventory with source releases. Preserve the pinned
   upstream notices and each copied component's copyright text.
3. Keep the matching `META-INF/LICENSE`, `META-INF/NOTICE`, `META-INF/THIRD_PARTY_LICENSES.md`
   copies in sync with root documents. The standard JAR includes those and the canonical license
   directory without a custom packaging task.
4. Ship runtime dependencies or provide resolvable dependency metadata. The current plain JAR
   embeds neither dependencies nor a Maven POM. No remote publication is configured.
5. Preserve legal files when shading, relocating or embedding. Preserve native resources and
   ImageIO service files. Re-audit actual bundled contents, including platform-specific artifacts.
6. Exclude `external/examples/`, the model repository and generated/decrypted model outputs from
   releases unless their separate permissions explicitly allow that distribution.

This inventory covers the declared Java dependency graph and retained port provenance. Before a
public binary release, confirm contributor authority for the MIT grant and any additional copied
source introduced later. A changed native build, shaded artifact or upstream submodule requires
another content and notice audit. The unmodified upstream copyright line is preserved despite
its bracketed style. Do not replace it with an inferred individual author's name.
