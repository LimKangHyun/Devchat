package project.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NumberedFileContentTest {

    @Test
    void 줄_번호는_한_번만_붙는다() {
        String numbered = GeminiClient.numberedFileContent("class A {\n  void run() {}\n}");

        assertThat(numbered).isEqualTo("   1: class A {\n   2:   void run() {}\n   3: }\n");
    }
}
