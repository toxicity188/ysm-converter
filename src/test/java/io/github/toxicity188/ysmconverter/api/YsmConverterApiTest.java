package io.github.toxicity188.ysmconverter.api;

import io.github.toxicity188.ysmconverter.parser.YsmParser;
import io.github.toxicity188.ysmconverter.format.YsmVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class YsmConverterApiTest {
    @TempDir Path temporary;

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
        assertThrows(NullPointerException.class, () -> YsmConverter.parse((InputStream) null));
        assertThrows(NullPointerException.class, () -> YsmConverter.decrypt((InputStream) null));
        assertThrows(NullPointerException.class, () -> YsmConverter.decrypt((byte[]) null));
        assertThrows(NullPointerException.class, () -> YsmConverter.decrypt((Path) null));
    }

    @Test
    void memoryOverloadsAndExtractionPreserveResourcesAndMetadata() throws Exception {
        var name = "nested/value.json";
        var content = "{\"value\":42}".getBytes(StandardCharsets.UTF_8);
        var encrypted = encryptedResource(name, content);
        var original = encrypted.clone();
        var source = temporary.resolve("input.ysm");
        Files.write(source, encrypted);
        var stream = new ByteArrayInputStream(encrypted) {
            boolean closed;

            @Override
            public void close() {
                closed = true;
            }
        };
        var bytesResult = YsmConverter.parse(encrypted);
        var streamResult = YsmConverter.parse(stream);
        var pathResult = YsmConverter.parse(source);
        assertFalse(stream.closed);
        assertEquals(YsmVersion.V1, bytesResult.version());
        assertEquals(1, bytesResult.formatVersion());
        assertTrue(bytesResult.warnings().isEmpty());
        for (var result : new YsmResourceResult[]{streamResult, pathResult}) {
            assertEquals(bytesResult.version(), result.version());
            assertEquals(bytesResult.formatVersion(), result.formatVersion());
            assertEquals(bytesResult.warnings(), result.warnings());
            assertResources(bytesResult.resources(), result.resources());
        }
        assertArrayEquals(content, bytesResult.resources().get(name));
        assertResources(bytesResult.resources(), YsmConverter.decrypt(encrypted));
        assertResources(bytesResult.resources(), YsmConverter.decrypt(source));
        stream.reset();
        assertResources(bytesResult.resources(), YsmConverter.decrypt(stream));
        assertFalse(stream.closed);
        assertArrayEquals(original, encrypted);
        assertThrows(UnsupportedOperationException.class, () -> bytesResult.resources().clear());
        try (var paths = Files.list(temporary)) {
            assertEquals(1, paths.count(), "Memory parsing must not extract resources");
        }
        var fromBytes = YsmConverter.parse(encrypted, temporary.resolve("bytes"));
        var fromPath = YsmConverter.parse(source, temporary.resolve("path"));
        for (var result : new YsmParseResult[]{fromBytes, fromPath}) {
            assertEquals(bytesResult.version(), result.version());
            assertEquals(bytesResult.formatVersion(), result.formatVersion());
            assertEquals(bytesResult.warnings(), result.warnings());
            assertEquals(1, result.resourceCount());
            assertArrayEquals(content, Files.readAllBytes(result.extractedFiles().getFirst()));
        }
    }

    @Test
    void streamReadFailurePropagatesWithoutClosingCallerStream() {
        var failure = new IOException("read failed");
        var input = new InputStream() {
            boolean closed;

            @Override
            public int read() throws IOException {
                throw failure;
            }

            @Override
            public void close() {
                closed = true;
            }
        };
        assertSame(failure, assertThrows(IOException.class, () -> YsmConverter.parse(input)));
        assertSame(failure, assertThrows(IOException.class, () -> YsmConverter.decrypt(input)));
        assertFalse(input.closed);
    }

    private static void assertResources(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((name, bytes) -> assertArrayEquals(bytes, actual.get(name), name));
    }

    private static byte[] encryptedResource(String name, byte[] contents) throws Exception {
        var compressed = new ByteArrayOutputStream();
        try (var deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(contents);
        }
        var key = new byte[16];
        var iv = new byte[16];
        var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        var encrypted = cipher.doFinal(compressed.toByteArray());
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeBytes("YSGP");
            output.writeInt(1);
            output.write(new byte[16]);
            var filename = name.getBytes(StandardCharsets.UTF_8);
            output.writeInt(filename.length);
            output.write(filename);
            output.writeInt(encrypted.length);
            output.write(key);
            output.write(iv);
            output.write(encrypted);
        }
        return bytes.toByteArray();
    }
}
