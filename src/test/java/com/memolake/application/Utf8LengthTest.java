package com.memolake.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Utf8LengthTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "ascii", "é ß", "한글", "🎉 emoji", "mixed 한 é 🎉\n\t"})
    void matchesEncodedLength(String text) {
        assertThat(MemoService.utf8Length(text)).isEqualTo(text.getBytes(StandardCharsets.UTF_8).length);
    }
}
