# Publishing YSM Converter

The target public repository is `https://github.com/toxicity188/ysm-converter`. Publishing is
configured locally. No Maven Central upload, GitHub Packages upload, GitHub release or hosted
JitPack build has been performed during setup.

## Coordinates and version

| Repository | Coordinates | Build/signing |
| --- | --- | --- |
| Maven Central, primary | `io.github.toxicity188:ysm-converter:<version>` | Signed Maven publication through Central Portal |
| GitHub Packages | Same coordinates | Same signed publication through repository-specific Gradle task |
| JitPack | `com.github.toxicity188:ysm-converter:<tag>` | Public tag build, unsigned local publication inside JitPack |

Set `version` in root `gradle.properties`. Release tags must be identical, for example `1.0.0`.
Do not add a `v` prefix or strip version suffixes per repository. The `verifyReleaseVersion` task
requires the supplied `releaseTag` to equal the non-SNAPSHOT project version. Pre-release versions
such as `1.0.0-rc1` can use the same complete string. Stable release workflows reject snapshots.

`JITPACK=true` switches only the JitPack build's group to `com.github.toxicity188`. Its version
still comes from `gradle.properties` and must match JitPack's `VERSION` environment variable.
Central and GitHub Packages retain the `io.github.toxicity188` group in normal builds.

## Artifacts and dependencies

The single Java library publishes a binary JAR, `-sources.jar`, `-javadoc.jar`, POM and Gradle
module metadata. Signed publishing adds detached `.asc` files. All three JARs carry `META-INF/LICENSE`,
`NOTICE`, `THIRD_PARTY_LICENSES.md` and the full runtime/provenance license directory. Source JARs
contain main sources/resources, and Javadoc JARs contain generated API documentation.

Gson is a compile/API dependency because public methods expose Gson types. Zstd-jni and the
TwelveMonkeys modules are runtime dependencies. JetBrains annotations remain compile-only.
JUnit and other test dependencies do not appear in the published POM. The ordinary JAR contains
the library's code and resources. Model repositories, example assets, extracted/converted models
and dependency classes are excluded from these artifacts.

## Maven Central account

1. Create a Central Portal account and verify control of the `io.github.toxicity188` namespace
   using the Portal's GitHub ownership procedure for `toxicity188`.
2. Generate a Portal user token. Its username and password become `mavenCentralUsername` and
   `mavenCentralPassword`. Use the token credentials supplied by the Portal.
3. Generate or select a GPG signing key controlled by the maintainer. Publish its public key to
   a keyserver supported by Central. Confirm the public fingerprint and expiry before release.
4. Export the ASCII-armored private signing key for CI and store it as an environment secret.
   Keep a protected backup and use a suitable expiry/rotation policy.

The plugin uses the current Central Portal API. No legacy OSSRH host configuration is needed.
By default, `publishToMavenCentral` uploads for manual release in the Portal.
`publishAndReleaseToMavenCentral` requests automatic release, and is the command used by the
explicit release workflow. Credentials for GitHub Packages are only needed for its own task.
[Central namespaces](https://central.sonatype.org/register/namespace/),
[Portal tokens](https://central.sonatype.org/publish/generate-portal-token/),
[GPG requirements](https://central.sonatype.org/publish/requirements/gpg/),
[publishing plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)

## Secrets and local settings

Create the following GitHub Secrets in the `release` environment or repository, as appropriate.
The workflow's publishing job uses the `release` environment.

| Secret | Gradle property/environment mapping | Purpose |
| --- | --- | --- |
| `MAVEN_CENTRAL_USERNAME` | `ORG_GRADLE_PROJECT_mavenCentralUsername` | Portal user-token username |
| `MAVEN_CENTRAL_PASSWORD` | `ORG_GRADLE_PROJECT_mavenCentralPassword` | Portal user-token password |
| `SIGNING_KEY` | `ORG_GRADLE_PROJECT_signingInMemoryKey` | ASCII-armored private signing key |
| `SIGNING_PASSWORD` | `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` | Passphrase, empty only for an unencrypted key |
| `GITHUB_TOKEN` | Automatically supplied by Actions | GitHub Packages upload with `packages: write` |

The maintainer workflow needs no manually stored GitHub PAT for its own repository. Local
GitHub Packages uploads/downloads use a personal access token with the applicable package scope
and repository authorization. Package visibility and repository access must be configured after
the first package is created. Check organization SSO requirements when relevant.
[GitHub Maven registry authentication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)

For local use, place secrets in the user's `~/.gradle/gradle.properties`, or prefix the property
names with `ORG_GRADLE_PROJECT_` when exporting environment variables. Example property names
with placeholders are shown below. Do not copy real values into the repository.

```properties
mavenCentralUsername=<portal-token-username>
mavenCentralPassword=<portal-token-password>
signingInMemoryKey=<ASCII-armored-private-key-with-newlines-escaped-as-\n>
signingInMemoryKeyPassword=<passphrase>
githubPackagesUsername=<github-username>
githubPackagesPassword=<authorized-token>
```

An optional `signingInMemoryKeyId` can select a signing subkey when required. The normal workflow
lets the plugin select the key from `SIGNING_KEY`. Export a key with `gpg --armor --export-secret-keys
<fingerprint>` in a trusted environment. Multiline GitHub Secrets can retain the armor and newlines.
Never put signing material in a command-line property, commit, build log or published artifact.
Signing jobs disable the configuration cache and Gradle Actions cache to avoid storing secret
configuration in shared caches. Remove temporary exports after installing the secret.

Signing is enabled by default. Unsigned local/JitPack verification uses `-PsigningEnabled=false`.
Repository publication tasks reject that setting. A normal build does not need publishing
credentials. An unsigned local publication also needs no key or repository credentials.

## GitHub Actions

`ci.yml` runs build/tests and unsigned `publishToMavenLocal` on branch pushes, pull requests and
manual dispatch. It has read-only repository permissions and no remote publication command.
External models are optional and are not downloaded or committed by the workflow.

`release.yml` runs on a **published GitHub release** or explicit **workflow_dispatch**. A tag push
alone does not upload. It checks out the tag, verifies its version, and builds/tests before the
publishing job. The publishing job uses the `release` environment with `packages: write` permission.
Set required reviewers and suitable deployment rules in that environment if manual approval is
desired. Publishing a GitHub release is already an explicit remote-release trigger.

Manual dispatch requires an existing tag and offers `both`, `central` or `github` destinations.
For a partial failure, rerun only the missing destination. Released Central versions are immutable,
so do not attempt to replace an existing version. Concurrency serializes runs for the same tag.
The workflow does not create tags or GitHub releases itself.

## JitPack

`jitpack.yml` selects OpenJDK 21 and runs the wrapper's release-version verification and unsigned
local publication. It skips tests in the hosted artifact build because optional private/local model
fixtures are unavailable. CI performs the test build independently. The hosted install uses
`--no-configuration-cache` and needs no Central, GitHub Packages or signing secrets.

After the repository is public, request an exact matching tag at JitPack and inspect the hosted
build log and artifacts. The local equivalent below validates the installation step. A live
JitPack build can only be confirmed after the future repository/tag is accessible.
[JitPack build configuration](https://docs.jitpack.io/building/)

## Local verification

```powershell
.\gradlew build
.\gradlew verifyReleaseVersion '-PreleaseTag=1.0.0'
.\gradlew publishToMavenLocal -PsigningEnabled=false
```

To isolate verification from your normal Maven cache, add
`'-Dmaven.repo.local=C:/absolute/path/to/temporary-m2'` on PowerShell, or the equivalent quoted
absolute path on your shell. The default Maven local directory is `~/.m2/repository`.
Inspect `io/github/toxicity188/ysm-converter/<version>/` for the JARs, POM and `.module` file.
Check dependencies, license/SCM/developer metadata, license entries and absence of model assets.
Compile a consumer that imports `JsonElement` using only the library dependency, then execute a
real memory conversion with the transitive runtime classpath.

For signed verification, provide a disposable local key and run `publishToMavenLocal` with default
signing enabled and `--no-configuration-cache`. Verify each `.asc` using `gpg --verify` and the
test public key. A disposable key verifies the mechanism and does not establish trust for a real
release. Repeat signing with the maintainer's production key before the first public release.

Local setup verification on 2026-11-16 passed the full 68-test build, including the available
906-model compatibility harnesses. Binary, source and Javadoc JARs contain the three matching
project legal documents and 16 license/attribution files each. All five signatures (three JARs,
POM and Gradle metadata) verified with a disposable GPG key. Gradle-metadata and POM-only consumers
compiled with the single library dependency and converted an actual V3 model, including native
Zstd and ImageIO providers. A mismatched tag failed verification. The JitPack install command was
simulated locally with its environment variables. Both workflows passed actionlint 1.7.12.
Hosted Actions execution, production-key trust and repository acceptance await the first release.

## First public release

1. Review and commit the publishing configuration and legal documents. Create the public
   `toxicity188/ysm-converter` repository with the expected `main` branch and license URLs.
2. Complete the namespace, public GPG key, Secrets and `release` environment setup above.
3. Set the intended version in `gradle.properties`, run full available tests and local artifact
   verification, and review the generated POM and all three JARs.
4. Commit the version, create the identical Git tag, and push the reviewed commit/tag. Avoid
   moving a released tag. Tag push performs no upload in these workflows.
5. Publish a GitHub release for that tag or manually dispatch `Publish release` for the tag.
   Approve the environment deployment if reviewers are configured.
6. Confirm Central validation/public availability and GitHub Packages version/access. Request
   the same tag from JitPack and inspect its build. Verify consumers against the chosen repository.
7. Update README availability statements only after the public artifacts resolve successfully.

## License policy

The original YSMParser MIT copyright and full permission text remain intact. toxicity188 grants
MIT permissions for Java porting work and original additions to the extent authored by toxicity188.
The separate contributor notice and all original third-party notices remain. The POM describes
the project's MIT license and links to this repository's complete `LICENSE`. Dependencies retain
the additional terms in `NOTICE` and `THIRD_PARTY_LICENSES.md`.

`Yes-Steve-Model-Repo` and `external/examples` are external development/test fixtures. Their assets
are absent from library source/binary/Javadoc artifacts. Their individual redistribution terms
do not change the library's MIT policy. A downstream engine distributing models must obtain the
applicable model permissions separately.

The setup follows DynamicUV's Vanniktech-based publication configuration, adapted to this
single-project Java library and one unchanged release version across repositories.
[DynamicUV reference](https://github.com/toxicity188/DynamicUV/blob/master/library/build.gradle.kts)
