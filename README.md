## Blockbench conversion

Convert resources already extracted by the Java API into a self-contained, editable `.bbmodel`:

```java
var parser = YsmParser.of(Path.of("model.ysm"));
parser.parse();
var result = BlockbenchConverter.convert(parser.getExtractedResources());
result.write(Path.of("output/model.bbmodel"));
result.warnings().forEach(System.out::println);
```

Use `io.github.toxicity188.ysmconverter.parser.YsmParser`,
`io.github.toxicity188.ysmconverter.blockbench.BlockbenchConverter`, and `java.nio.file.Path`.
An extracted directory can also be passed to `BlockbenchConverter.convert(Path)`.
Only third-person player geometry is converted; arm resources, projectile models and TAC animations are excluded.
See [conversion rules and verification](docs/blockbench-conversion.md) for supported data and limitations.

## Credits

This project is a Java rewrite of
[YSMParser](https://github.com/OpenYSM/YSMParser).

The original YSMParser project is developed by the OpenYSM team
and licensed under the MIT License.

This project is not officially affiliated with OpenYSM.
