package com.memolake.interfaces.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemoViewTest {

    @Test
    void parsesQueryParameterValues() {
        assertThat(MemoView.parse("raw")).contains(MemoView.RAW);
        assertThat(MemoView.parse(" Markdown ")).contains(MemoView.MARKDOWN);
        assertThat(MemoView.parse("HTML")).contains(MemoView.HTML);
        assertThat(MemoView.parse("pdf")).isEmpty();
        assertThat(MemoView.parse(null)).isEmpty();
    }

    @Test
    void detectsTheDefaultViewFromContent() {
        assertThat(MemoView.detect("# title\n\ntext")).isEqualTo(MemoView.MARKDOWN);
        assertThat(MemoView.detect("plain text")).isEqualTo(MemoView.MARKDOWN);
        assertThat(MemoView.detect("")).isEqualTo(MemoView.RAW);
        assertThat(MemoView.detect(null)).isEqualTo(MemoView.RAW);
        assertThat(MemoView.detect("\n  {\"a\": 1}\n")).isEqualTo(MemoView.RAW);
        assertThat(MemoView.detect("[1, 2]")).isEqualTo(MemoView.RAW);
        assertThat(MemoView.detect("[link](x) and more")).isEqualTo(MemoView.MARKDOWN);
        assertThat(MemoView.detect("<!DOCTYPE html><html></html>")).isEqualTo(MemoView.HTML);
        assertThat(MemoView.detect("<html lang=\"ko\">...")).isEqualTo(MemoView.HTML);
        assertThat(MemoView.detect("<b>bold</b> fragment")).isEqualTo(MemoView.MARKDOWN);
    }
}
