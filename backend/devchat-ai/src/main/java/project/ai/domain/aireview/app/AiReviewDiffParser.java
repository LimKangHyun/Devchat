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

    /**
     * 단일 파일 diff에서 실제 변경된 라인 번호(+로 시작하는 라인)를 추출한다.
     * Stream으로 파일 하나씩 받아서 처리하므로 Map이 아닌 Set으로 반환한다.
     *
     * @param fileDiff 파일 하나의 diff 문자열
     * @return 변경된 라인 번호 집합
     */
    public Set<Integer> parseValidLines(String fileDiff) {
        Set<Integer> validLines = new LinkedHashSet<>();
        int headLineNum = 0;

        for (String line : fileDiff.split("\n")) {
            if (line.startsWith("@@ ")) {
                // @@ -old +new @@ 형식에서 새 파일 시작 라인 번호 추출
                Matcher m = Pattern.compile("\\+([0-9]+)").matcher(line);
                if (m.find()) headLineNum = Integer.parseInt(m.group(1)) - 1;
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                // 추가된 라인 → 유효한 리뷰 대상
                headLineNum++;
                validLines.add(headLineNum);
            } else if (!line.startsWith("-") && !line.startsWith("diff") && !line.startsWith("index") && !line.startsWith("---")) {
                // 컨텍스트 라인(변경 없음) → 라인 번호만 증가
                headLineNum++;
            }
            // 삭제된 라인(-)은 새 파일 기준 라인 번호에 영향 없으므로 스킵
        }
        return validLines;
    }

    /**
     * Gemini가 반환한 lineNumber가 GitHub diff 상 변경 라인이 아닐 수 있다.
     * GitHub PR 리뷰 API는 diff에서 실제 변경된 라인(+)에만 코멘트를 허용하므로,
     * 유효한 변경 라인 중 가장 가까운 라인으로 매핑한다.
     *
     * @param validLines 유효한 diff 라인 번호 집합
     * @param target Gemini가 반환한 라인 번호
     * @return 가장 가까운 유효 라인 번호
     */
    public int findNearestDiffLine(Set<Integer> validLines, int target) {
        return validLines.stream()
                .min(Comparator.comparingInt(l -> Math.abs(l - target)))
                .orElse(target);
    }

    /**
     * Gemini가 반환한 리뷰 목록에서 유효하지 않은 항목을 제거한다.
     * - fileContent 범위를 벗어난 lineNumber 제거
     * - 빈 comment 제거
     *
     * @param reviews Gemini가 반환한 인라인 리뷰 목록
     * @param fileContent 파일 전체 내용 (라인 수 검증용)
     * @return 유효한 리뷰 목록
     */
    public List<InlineReview> filterValidReviews(List<InlineReview> reviews, String fileContent) {
        String[] lines = fileContent.split("\n");
        int totalLines = lines.length;

        return reviews.stream()
                .filter(review -> {
                    if (review.lineNumber() < 1 || review.lineNumber() > totalLines) {
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