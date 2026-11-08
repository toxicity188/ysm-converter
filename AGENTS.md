# Java Coding and Javadoc Guidelines

This document defines coding style and documentation conventions for automated contributors working on the Java rewrite of YSMParser.

Scope: the entire repository unless a more specific `AGENTS.md` overrides it.

## Reference Implementation

- `external/YSMParser` is a Git submodule containing the original C++ implementation.
- Use it as the authoritative reference for the Java rewrite.
- Do not modify files inside `external/YSMParser`.
- Do not introduce runtime or build-time dependencies on the original C++ implementation.
- Implement the required functionality independently in Java 21.
- Preserve behavioral compatibility with the original implementation.

## 1. Language and Compatibility

### MUST
- Use Java 21 as the source and target language level.
- Write production code in Java only. Do not introduce Kotlin source files or Kotlin-specific build tooling.
- Keep Java code compatible with Java 21; do not use APIs or language features introduced in later releases.
- Follow the package structure, naming conventions, import organization, formatting, and nullability conventions already established in the repository.

## 2. Java Code Style

### MUST
- Write English Javadoc for public API types, constructors, and methods.
- Update Javadoc whenever a public API is changed.
- Use `var` for local variables when legal and the inferred type remains clear.
- Declare classes `final` when they are not designed for inheritance.
- Name factory methods `of(...)` where appropriate.
- Prefer enum-based singletons when a singleton is genuinely needed.
- If the project already uses fastutil, prefer its primitive-specialized collections over boxed `java.util` collections for primitive-heavy data structures.

### RECOMMENDED
- Prefer `record` for immutable data carriers.
- Where practical, provide a factory method on an interface rather than exposing implementation details.
- Prefer composition and small, focused types over inheritance-heavy designs.
- Follow the surrounding code style instead of reformatting unrelated files.

### DISCOURAGED
- Unnecessary inheritance hierarchies and speculative abstractions.
- Broad formatting or naming changes unrelated to the task.

## 3. Javadoc Policy

### MUST
- Write all Javadoc in English.
- Use `@since` for public APIs, following the version declared in `gradle.properties` when the project uses that versioning convention. If the versioning policy has not been established, do not invent a version; establish the policy first.
- Include `@return` for methods that return a value.
- Document every `record` component with `@param` in the record's type-level Javadoc.
- Include `@throws` for exceptions that public API methods explicitly throw or are expected to propagate as part of their contract.
- Provide at least one practical usage example for each public API type or API entry point. Prefer standard Javadoc `{@code ...}` or `<pre>{@code ...}</pre>` blocks rather than custom tags.
- Keep terminology consistent with the project domain, including YSM, model decryption, binary parsing, geometry reconstruction, and Blockbench conversion.
- Ensure documentation accurately reflects behavior and does not promise unsupported format versions or guarantees.

### RECOMMENDED
- Describe parameter constraints and edge cases where they affect callers.
- Explain observable behavior, failure conditions, and important format limitations without repeating the implementation line by line.
- Keep examples concise and compilable with Java 21 whenever practical.

## 4. Existing Code Precedence

- Prefer established local conventions over generic preferences in this document when they conflict.
- Preserve behavior when adjusting documentation or style.
- Keep style-related changes focused on files relevant to the task.
