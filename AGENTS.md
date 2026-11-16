# Contributor instructions

Scope is the entire repository unless a more specific `AGENTS.md` applies. Read this file before
suggesting or modifying code. Preserve existing behavior and conversion accuracy through focused,
verified changes.

## Project structure

Production code is Java 21 under `src/main/java/io/github/toxicity188/ysmconverter/`.

| Package or path | Responsibility |
| --- | --- |
| `api` | `YsmConverter`, parser selection and public resource/result objects |
| `format` | Container version detection and V3 internal format tags |
| `binary` | Binary readers and primitive/vector representations |
| `crypto` | YSM-specific cipher, hash and random-generator operations |
| `compression` | Custom YSM Zstandard handling and standard decompression dispatch |
| `parser` | V1/V2/V3 resource deserialization, image handling and safe extraction paths |
| `model` | Geometry math and reconstruction from parsed binary structures |
| `blockbench` | Project assembly, Geometry/UV, Animation, UUID repair, textures and prop visibility |
| `src/main/resources/META-INF` | Packaged licenses, attribution and dependency inventory |
| `src/test/java` | Generated-data unit tests and local asset compatibility checks |
| `docs/blockbench-conversion.md` | Conversion rules, limitations and dated validation observations |
| `external/YSMParser` | Read-only C++ reference implementation, pinned Git submodule |
| `external/examples` | Read-only extracted examples and occasional original `.bbmodel` references |
| `external/Yes-Steve-Model-Repo` | Read-only third-party encrypted compatibility models |
| `build` | Generated classes, reports, extracted resources and local review artifacts |

## Pipeline and API boundaries

The supported pipeline is container detection, decryption, decompression, resource parsing,
then Blockbench conversion. `YsmConverter.parse(input)` returns `YsmResourceResult`, including
resources, version information and warnings. `decrypt(input)` returns the resource map directly.
Both accept `InputStream`, `byte[]` and `Path`. Caller-owned streams remain open on success and
failure. Path input conveniences own and close their streams.

`BlockbenchConverter.convert(Map<String, byte[]>)` returns `JsonElement`.
`convertWithWarnings(Map)` returns `BlockbenchResult`. Keep conversion in memory, including image
decode/encode. Use memory ImageIO streams to avoid implicit disk caches. Do not change global
ImageIO cache settings. Avoid new runtime or build-time dependencies on the C++ implementation.

Preserve existing Path conveniences and their observable behavior. `YsmConverter.parse(Path, Path)`
and `parse(byte[], Path)` perform extraction through the memory API. `BlockbenchConverter.convert(Path)`
reads an extracted directory. `BlockbenchResult.write(Path)` handles output. Keep filesystem writes
at explicit extraction/save boundaries and preserve path traversal and symlink protections.

Resource result maps are shallow snapshots with mutable byte arrays. Project JSON is mutable.
Do not promise deep immutability. Keep warnings available through result objects. Match established
exception contracts. Parsing uses `IOException` and specific unchecked malformed-input,
unsupported-format and integrity exceptions. Conversion wraps invalid-resource failures in
`IOException`. Do not silently convert malformed input into a valid-looking empty model.

## Java and design conventions

- Use Java 21 source/target compatibility and APIs. Production code stays Java. Retain the existing
  Kotlin DSL Gradle build without introducing Kotlin production sources or new Kotlin tooling.
- Follow surrounding package structure, imports, formatting and JetBrains nullability annotations.
- Use `var` for locals when legal and clear. Prefer records for immutable carriers and `final`
  classes unless inheritance is intentional. Name appropriate factories `of(...)`.
- Prefer enum-based singletons when a singleton is genuinely needed. Where practical, expose
  a factory on an interface rather than exposing implementation details.
- Reuse existing helpers and standard-library operations. Prefer composition and focused types.
  Avoid speculative abstractions, unnecessary hierarchies and unrelated formatting changes.
- If fastutil is already introduced for a justified need, prefer its primitive collections for
  primitive-heavy code. Its mere mention here is not a reason to add it as a dependency.
- Read callers and existing tests before changing shared behavior. Edit only the scope requested.
  Preserve existing source notices and document substantive port changes accurately.

## Javadoc

Write English Javadoc for public types, constructors and methods. Update it with API changes.
Document record components with type-level `@param`, return values with `@return`, and expected
contract exceptions with `@throws`. Include concise practical Java 21 usage examples. Keep YSM,
decryption, parsing, geometry reconstruction and Blockbench terminology consistent.

Use `@since` according to the established API version. The current project version is `1.0.0`
in `gradle.properties`, and existing public APIs use `@since 1.0.0`. Check the actual build version
when adding APIs. Follow that single version for all repositories. Do not invent
support guarantees or a new versioning policy in comments.

## Conversion invariants

- Select player resources using `ysm.json`/legacy `main.json` and established role rules. Current
  scope is third-person player geometry. Arm, projectile and TAC resources are excluded.
- Preserve Bone hierarchy, Pivot, rotations, cube dimensions, inflate and UV semantics. Keep the
  project version and its coordinate/sign migration rules coherent. Compare transformed shape
  and UVs with source/original projects when changing reconstruction math.
- Keep logical UV resolution distinct from decoded bitmap dimensions. Preserve signed UV sizes,
  mirror behavior, face rotations and deliberately missing faces. `texture: null` hides a face,
  while `texture: false` leaves a visible face unassigned. Save JSON with `serializeNulls()`.
- Keep valid explicit texture references and ordering. Fallback needs evidence and warnings.
  Do not resolve ambiguous images by map order or a sample-specific filename.
- Bone animator keys must resolve to actual nested outliner groups, excluding cube/locator UUID
  strings. Treat `effects` as a special animator. Preserve valid tracks and all recoverable keys.
  Repair stale UUIDs only with unambiguous evidence. Do not invent missing bones or pick among
  duplicate names. Unresolvable tracks and animations emptied by removal need diagnostics.
- Prop inference changes default editor `visibility` only. Keep geometry, UUIDs, hierarchy,
  Pivot and keyframes. Hide selected descendants consistently and verify they can be shown again.
  Default visibility is independent of runtime scale animation.
- Be conservative with props. Names, `scale: 0`, a hand parent or script references alone do not
  establish a prop. Preserve uncertain candidates and protect body, hair, clothing and expressions.
  `ysmGlow` is also used for facial and clothing details. GUI roles need independent metadata and
  animation evidence. Never assume arbitrary Molang variables default to zero.
- Runtime controllers, scripts, layered animations and query state exceed authoring reconstruction.
  Preserve supported editable data and disclose limitations. Do not claim game-runtime equivalence.

## Tests and visual verification

Unit tests must remain independent of external assets. Use small generated fixtures for specific
regressions. External data belongs in optional integration checks, with assumptions/skips when
fixtures are absent. Avoid creating assertions that merely mirror implementation decisions.

```powershell
.\gradlew test --tests '*api.*' --tests '*crypto.*' --tests '*compression.*' --tests '*format.*' --tests '*model.*' --tests '*parser.*' --tests '*BlockbenchConverterTest' --tests '*BlockbenchAnimationReferencesTest'
.\gradlew test --tests '*BlockbenchExamplesTest' --tests '*BlockbenchMemoryIntegrationTest'
.\gradlew test --tests 'io.github.toxicity188.ysmconverter.IntegrationTest'
.\gradlew build javadoc
```

Use `./gradlew` on Unix-like systems. Current wrapper is Gradle 9.9.1, with Java 21 bytecode.
The full build executes every test present locally. An optional local `BlockbenchTest` requires
the encrypted repository. It may not exist in a fresh checkout. Prop integration checks depend
on extraction output in `build/ysm-parser-tests/extracted/`, so run the extraction integration
test first when those fixtures are needed. Attachment checks use their documented local input.

Use several format versions and layouts. Check geometry and UV invariants, embedded texture
pixels, animator references, warnings and visibility. Report compatibility failures rather than
discarding failed samples. Original `.bbmodel` files are comparison oracles and must not become
conversion shortcuts. Keep all review outputs under ignored `build/` directories.

For visual changes, use the available Blockbench use/MCP overview/animation skills and a live MCP
server. Discover capabilities and format first. Preserve the user's project and inspect a new
review tab. Open generated `.bbmodel` through Blockbench's native project codec, then inspect
hierarchy, texture errors and unresolved animators. Render with a fixed offscreen camera. Compare
the source project under matching conditions when available. Sample multiple animation times
and loop seams. For visibility changes, show hidden subtrees again and verify complete geometry.
Use native undo transactions for temporary scene edits and restore inspection changes afterward.
When MCP is unavailable, state which visual checks remain pending and keep review files. A JSON
test passing alone does not establish correct rendering. Do not report an unperformed check.

## Third-party assets and release notices

Publishing uses `com.vanniktech.maven.publish` with `java-library`. Central and GitHub Packages
coordinates are `io.github.toxicity188:ysm-converter:<version>`. JitPack uses its own group and
the identical tag/version. Keep Gson as an API dependency because public methods expose its types.
Retain binary, sources and Javadoc artifacts, their license resources and signatures. See
`docs/publishing.md` for local verification and release triggers. Use local publishing for validation.
Remote uploads require an explicit release request or the configured release/manual trigger.
Never commit or log tokens, passphrases or private signing keys.

Use `external/YSMParser` as the behavioral reference for the Java port. Do not modify it or require
it to build/run the Java library. A task can limit reference analysis further. The original MIT
notice and component licenses must remain intact. Follow `LICENSE`, `NOTICE` and
`THIRD_PARTY_LICENSES.md`. Keep root notice documents synchronized with their packaged copies
under `src/main/resources/META-INF`. Recheck resolved dependency licenses when versions change.

Treat both model directories as read-only test data. Do not modify or redistribute source assets,
decrypted resources or converted models without appropriate permission. Exclude them from commits
and release artifacts unless authorized by their owners. Their licenses are separate from the
library's MIT terms. Document model failures and source-data differences without sample-specific
production overrides.
