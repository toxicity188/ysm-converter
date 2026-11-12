## Blockbench conversion

Decrypt and convert an encrypted YSM stream entirely in memory:

```java
Map<String, byte[]> resources = YsmConverter.decrypt(inputStream);
JsonElement model = BlockbenchConverter.convert(resources);
```

Use `io.github.toxicity188.ysmconverter.api.YsmConverter`,
`io.github.toxicity188.ysmconverter.blockbench.BlockbenchConverter`, `java.util.Map`, and `com.google.gson.JsonElement`.
`decrypt` accepts `InputStream`, `byte[]`, or `Path`. Caller-owned streams remain open.
Use `YsmConverter.parse(input)` to receive `YsmResourceResult` with resources, version information and parsing warnings.
Use `BlockbenchConverter.convertWithWarnings(resources)` for conversion warnings and optional `.write(Path)` output.
Existing `YsmConverter.parse(input, outputDirectory)` and `BlockbenchConverter.convert(Path)` convenience methods remain available.
Only third-person player geometry is converted; arm resources, projectile models and TAC animations are excluded.
See [conversion rules and verification](docs/blockbench-conversion.md) for supported data and limitations.

## Credits

This project is a Java rewrite of
[YSMParser](https://github.com/OpenYSM/YSMParser).

The original YSMParser project is developed by the OpenYSM team
and licensed under the MIT License.

This project is not officially affiliated with OpenYSM.
