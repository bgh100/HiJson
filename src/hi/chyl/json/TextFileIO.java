package hi.chyl.json;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Strict text decoding and non-destructive file replacement. */
final class TextFileIO {
    static final Charset UTF8 = StandardCharsets.UTF_8;
    static final Charset GBK = Charset.forName("GBK");
    private static final byte[] UTF8_BOM = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};

    record Decoded(String text, Charset charset, boolean bom) {}

    static Path selectedPath(String directory, String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        return (directory == null ? Path.of(fileName) : Path.of(directory).resolve(fileName))
            .toAbsolutePath().normalize();
    }

    private static boolean hasBom(byte[] bytes) {
        return bytes.length >= 3 && bytes[0] == UTF8_BOM[0]
            && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2];
    }

    static Decoded read(Path path, Charset explicitCharset) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        boolean bom = hasBom(bytes);
        if (explicitCharset != null) return decode(bytes, explicitCharset, bom);
        if (bom) return decode(bytes, UTF8, true);
        try {
            return decode(bytes, UTF8, false);
        } catch (CharacterCodingException invalidUtf8) {
            try {
                return decode(bytes, GBK, false);
            } catch (CharacterCodingException invalidGbk) {
                IOException error = new IOException("文件既不是有效的 UTF-8，也不是有效的 GBK；请检查文件编码。", invalidGbk);
                error.addSuppressed(invalidUtf8);
                throw error;
            }
        }
    }

    private static Decoded decode(byte[] bytes, Charset charset, boolean bom) throws CharacterCodingException {
        boolean utf8Bom = bom && charset.equals(UTF8);
        ByteBuffer input = ByteBuffer.wrap(bytes, utf8Bom ? 3 : 0, bytes.length - (utf8Bom ? 3 : 0));
        String text = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(input).toString();
        return new Decoded(text, charset, utf8Bom);
    }

    @FunctionalInterface
    interface AtomicMove {
        void move(Path source, Path target) throws IOException;
    }

    static void writeAtomic(Path target, String text, Charset charset, boolean bom, boolean overwrite) throws IOException {
        writeAtomic(target, text, charset, bom, overwrite,
            (source, destination) -> publish(source, destination, overwrite));
    }

    static void publish(Path source, Path destination, boolean overwrite) throws IOException {
        if (overwrite) {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } else {
            // ATOMIC_MOVE alone may replace an existing path, even without
            // REPLACE_EXISTING. A link publishes complete bytes without clobbering
            // a file created by another process after the save dialog closed.
            try {
                Files.createLink(destination, source);
            } catch (UnsupportedOperationException ex) {
                throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(),
                    "文件系统不支持安全创建文件，请选择其他保存位置。");
            }
        }
    }

    // The operation argument lets tests exercise unsupported/failed replacements.
    static void writeAtomic(Path target, String text, Charset charset, boolean bom,
                            boolean overwrite, AtomicMove operation) throws IOException {
        ByteBuffer encoded = charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
        int prefix = bom && charset.equals(UTF8) ? 3 : 0;
        byte[] output = new byte[prefix + encoded.remaining()];
        if (prefix != 0) System.arraycopy(UTF8_BOM, 0, output, 0, prefix);
        encoded.get(output, prefix, output.length - prefix);

        Path destination = target.toAbsolutePath().normalize();
        if (!overwrite && Files.exists(destination)) throw new FileAlreadyExistsException(destination.toString());
        Path temporary = Files.createTempFile(destination.getParent(), ".hijson-", ".tmp");
        try {
            Files.write(temporary, output);
            if (!overwrite && Files.exists(destination)) throw new FileAlreadyExistsException(destination.toString());
            operation.move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
