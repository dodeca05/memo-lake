package com.memolake.interfaces.rest;

import com.memolake.application.MemoService;
import com.memolake.common.exception.ErrorCode;
import com.memolake.common.exception.MemoLakeException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Reads a {@code text/plain} request body as the raw memo text.
 *
 * <p>The body is read with a hard byte limit (so an oversized body is rejected with 413 without being
 * buffered in full) and decoded strictly: invalid byte sequences are rejected instead of being replaced.
 * The charset comes from the {@code Content-Type} header and defaults to UTF-8.
 */
@Component
public class PlainTextBodyReader {

    private final MemoService memoService;

    public PlainTextBodyReader(MemoService memoService) {
        this.memoService = memoService;
    }

    public String read(HttpServletRequest request) {
        long maxBytes = memoService.maxContentBytes();
        if (request.getContentLengthLong() > maxBytes) {
            throw MemoLakeException.contentTooLarge(maxBytes);
        }
        Charset charset = charsetOf(request.getContentType());
        byte[] bytes;
        try (InputStream in = request.getInputStream()) {
            bytes = in.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE - 8));
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read request body", e);
        }
        if (bytes.length > maxBytes) {
            throw MemoLakeException.contentTooLarge(maxBytes);
        }
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        }
        catch (CharacterCodingException e) {
            throw MemoLakeException.invalidRequest("Request body is not valid " + charset.name() + " text");
        }
    }

    private static Charset charsetOf(String contentType) {
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }
        try {
            Charset charset = MediaType.parseMediaType(contentType).getCharset();
            return charset != null ? charset : StandardCharsets.UTF_8;
        }
        catch (IllegalArgumentException e) {
            // Malformed media type, or unknown / unsupported charset name.
            throw new MemoLakeException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported Content-Type: use text/plain; charset=UTF-8");
        }
    }
}
