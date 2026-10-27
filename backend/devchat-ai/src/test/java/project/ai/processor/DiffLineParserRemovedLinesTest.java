package project.ai.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiffLineParserRemovedLinesTest {

    @Test
    void 삭제된_줄은_변경_전_파일_기준_번호로_돌려준다() {
        String diff = String.join("\n",
            "--- a/A.java",
            "+++ b/A.java",
            "@@ -10,4 +10,3 @@",
            " keep1",
            "-removed1",
            "+added1",
            " keep2",
            "-removed2",
            "@@ -30,2 +29,2 @@",
            "-removed3",
            "+added3",
            " keep3");
        assertThat(new DiffLineParser().parseRemovedLines(diff)).containsExactly(11, 13, 30);
    }
}
