package com.memolake.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memolake.common.exception.MemoLakeException;
import java.text.Normalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NameRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "backend-project", "agent_a_progress", "연구 노트", "Topic 2026", "ㄱㄴ", "a b  c"})
    void validNames(String name) {
        assertThat(NameRules.isValid(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " a", "a ", "a/b", "a\\b", "a.b", "..", "a\tb", "a\nb", "a\u0000b", "a?b", "a#b",
            "a%2Fb", "a:b", "日本", "é"})
    void invalidNames(String name) {
        assertThat(NameRules.isValid(name)).isFalse();
    }

    @Test
    void maxLengthIs100Characters() {
        assertThat(NameRules.isValid("가".repeat(100))).isTrue();
        assertThat(NameRules.isValid("가".repeat(101))).isFalse();
        assertThat(NameRules.isValid(null)).isFalse();
    }

    @Test
    void decomposedHangulIsNormalizedToComposedForm() {
        String decomposed = Normalizer.normalize("한글", Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo("한글");

        assertThat(NameRules.requireTopicName(decomposed)).isEqualTo("한글");
    }

    @Test
    void invalidNameThrowsInvalidRequest() {
        assertThatThrownBy(() -> NameRules.requireMemoName("a/b"))
                .isInstanceOfSatisfying(MemoLakeException.class,
                        e -> assertThat(e.getMessage()).startsWith("Invalid memo name."));
    }
}
