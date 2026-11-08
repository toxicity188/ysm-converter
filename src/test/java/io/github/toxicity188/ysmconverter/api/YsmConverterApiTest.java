package io.github.toxicity188.ysmconverter.api;

import io.github.toxicity188.ysmconverter.parser.YsmParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class YsmConverterApiTest {

    @Test
    void testNullArgumentsValidation() {
        assertThrows(NullPointerException.class, () -> YsmConverter.parse((Path) null, Path.of("out")));
        assertThrows(NullPointerException.class, () -> YsmConverter.parse(Path.of("model.ysm"), null));
        assertThrows(NullPointerException.class, () -> YsmConverter.parse((byte[]) null, Path.of("out")));
        assertThrows(NullPointerException.class, () -> YsmConverter.parse(new byte[10], null));

        assertThrows(NullPointerException.class, () -> YsmParser.of((Path) null));
        assertThrows(NullPointerException.class, () -> YsmParser.of((byte[]) null));
        assertThrows(NullPointerException.class, () -> YsmParserFactory.create((Path) null));
        assertThrows(NullPointerException.class, () -> YsmParserFactory.create((byte[]) null));
    }
}
