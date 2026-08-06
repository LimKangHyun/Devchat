package project.ai.domain.aireview.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.common.dto.InlineReview;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AiReviewDiffParser {

    private static final int MAX_LINE_DISTANCE = 20;

    public Set<Integer> parseValidLines(String fileDiff) {
        Set<Integer> validLines = new LinkedHashSet<>();
        int headLineNum = 0;

        for (String line : fileDiff.split("\n")) {
            if (line.startsWith("@@ ")) {
                Matcher m = Pattern.compile("\\+([0-9]+)").matcher(line);
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
     * → 호출 측(AiReviewProcessor)에서 전체 코멘트로 전환한다.
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

    public List<InlineReview> filterValidReviews(List<InlineReview> reviews, String fileContent) {
        int totalLines = fileContent.split("\n").length;

        return reviews.stream()
            .filter(review -> {
                if (review.lineNumber() == null || review.lineNumber() < 1 || review.lineNumber() > totalLines) {
                    log.warn("lineNumber 범위 초과 제거: lineNumber={}, totalLines={}", review.lineNumber(), totalLines);
                    return false;
                }
                if (review.comment() == null || review.comment().isBlank()) {
                    log.warn("빈 comment 제거: lineNumber={}", review.lineNumber());
                    return false;
                }
                return true;
            })
            .toList();
    }
}