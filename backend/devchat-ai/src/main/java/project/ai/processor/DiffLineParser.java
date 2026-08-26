package project.ai.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * unified diff 텍스트를 파싱해서 유효 변경 라인을 추출한다.
 * AI 리뷰를 포함해 어떤 도메인에서도 재사용 가능한 순수 diff 유틸.
 */
@Slf4j
@Component
public class DiffLineParser {

    private static final int MAX_LINE_DISTANCE = 20;
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile("\\+([0-9]+)");

    public Set<Integer> parseValidLines(String fileDiff) {
        Set<Integer> validLines = new LinkedHashSet<>();
        int headLineNum = 0;

        for (String line : fileDiff.split("\n")) {
            if (line.startsWith("@@ ")) {
                Matcher m = HUNK_HEADER_PATTERN.matcher(line);
                if (m.find()) headLineNum = Integer.parseInt(m.group(1)) - 1;
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                headLineNum++;
                validLines.add(headLineNum);
            } else if (!line.startsWith("-") && !line.startsWith("diff") && !line.startsWith("index") && !line.startsWith("---")) {
                headLineNum++;
            }
        }
        return validLines;
    }

    /**
     * 유효한 변경 라인 중 target과 가장 가까운 라인을 찾는다.
     * 20줄 이내에 유효 라인이 없으면 빈 값을 반환한다.
     * → 호출 측에서 전체 코멘트로 전환할지 판단한다.
     */
    public OptionalInt findNearestDiffLine(Set<Integer> validLines, int target) {
        if (validLines.contains(target)) return OptionalInt.of(target);

        int closest = -1;
        int minDist = Integer.MAX_VALUE;

        for (int line : validLines) {
            int dist = Math.abs(line - target);
            if (dist < minDist) {
                minDist = dist;
                closest = line;
            }
        }

        return minDist <= MAX_LINE_DISTANCE ? OptionalInt.of(closest) : OptionalInt.empty();
    }
}