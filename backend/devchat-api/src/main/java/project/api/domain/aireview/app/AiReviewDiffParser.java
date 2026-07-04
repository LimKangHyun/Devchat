package project.api.domain.aireview.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AiReviewDiffParser {

    private static final Pattern HUNK_HEADER_PATTERN =
            Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    /**
     * 전체 PR diff + 변경 후(head) 파일 전체 내용을 이용해 파일별 변경 전(before) 내용을 "완전 복원"
     * - 헝크(@@) 밖 구간: afterFileContents에서 그대로 복사 (diff에 안 나오지만 변경 안 된 부분)
     * - 헝크(@@) 안 구간: diff의 context(' ')/removed('-') 라인으로 복원, added('+') 라인은 스킵
     *
     * @param fullDiff PR 전체 unified diff
     * @param afterFileContents 파일 경로 -> 변경 후(head) 전체 파일 내용
     */
    public Map<String, String> parseBeforeContents(String fullDiff, Map<String, String> afterFileContents) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, String> fileDiffs = parseFileDiffs(fullDiff);

        for (Map.Entry<String, String> entry : fileDiffs.entrySet()) {
            String file = entry.getKey();
            String afterContent = afterFileContents.get(file);
            if (afterContent == null) {
                log.warn("after content 없음, before 복원 스킵: file={}", file);
                continue;
            }
            result.put(file, reconstructBeforeContent(entry.getValue(), afterContent));
        }
        return result;
    }

    private String reconstructBeforeContent(String fileDiff, String afterContent) {
        String[] afterLines = afterContent.split("\n", -1);
        List<String> beforeLines = new ArrayList<>();
        int newCursor = 1; // afterLines 기준 1-indexed, "아직 안 옮긴 다음 라인" 포인터

        String[] diffLines = fileDiff.split("\n");
        int i = 0;
        while (i < diffLines.length) {
            Matcher m = HUNK_HEADER_PATTERN.matcher(diffLines[i]);
            if (!m.find()) {
                i++; // diff --git / index / --- / +++ 등 헤더 라인 스킵
                continue;
            }

            int newStart = Integer.parseInt(m.group(1));
            // 헝크 시작 전까지 안 건드린 구간은 afterContent에서 그대로 복사
            while (newCursor < newStart) {
                beforeLines.add(getLine(afterLines, newCursor));
                newCursor++;
            }
            i++;

            // 헝크 본문 처리 (다음 @@ 또는 다음 diff --git 나올 때까지)
            while (i < diffLines.length
                    && !diffLines[i].startsWith("@@")
                    && !diffLines[i].startsWith("diff --git")) {
                String line = diffLines[i];
                if (line.startsWith("-")) {
                    beforeLines.add(line.substring(1));           // removed → before에 포함
                } else if (line.startsWith("+")) {
                    newCursor++;                                  // added → after에서만 소비, before엔 없음
                } else if (!line.startsWith("\\")) {               // "\ No newline at end of file" 등은 무시
                    beforeLines.add(line.isEmpty() ? "" : line.substring(1)); // context
                    newCursor++;
                }
                i++;
            }
        }

        // 마지막 헝크 이후 남은 구간도 그대로 복사
        while (newCursor <= afterLines.length) {
            beforeLines.add(getLine(afterLines, newCursor));
            newCursor++;
        }

        return String.join("\n", beforeLines);
    }

    private String getLine(String[] lines, int oneIndexed) {
        int idx = oneIndexed - 1;
        return (idx >= 0 && idx < lines.length) ? lines[idx] : "";
    }

    /**
     * 전체 PR diff를 파일별로 Map형태로 쪼갬
     */
    public Map<String, String> parseFileDiffs(String fullDiff) {
        Map<String, String> fileDiffs = new LinkedHashMap<>();
        String[] parts = fullDiff.split("(?=diff --git )");
        for (String part : parts) {
            if (part.isBlank()) continue;
            Matcher matcher = Pattern.compile("diff --git a/.+ b/(.+)").matcher(part);
            if (matcher.find()) fileDiffs.put(matcher.group(1).trim(), part);
        }
        return fileDiffs;
    }

    public Map<String, Set<Integer>> parseDiffLines(String fullDiff) {
        Map<String, Set<Integer>> result = new LinkedHashMap<>();
        String currentFile = null;
        int headLineNum = 0;

        for (String line : fullDiff.split("\n")) {
            if (line.startsWith("diff --git")) {
                String[] parts = line.split(" b/");
                if (parts.length > 1) {
                    currentFile = parts[1].trim();
                    result.put(currentFile, new LinkedHashSet<>());
                }
            } else if (line.startsWith("@@ ")) {
                Matcher m = Pattern.compile("\\+([0-9]+)").matcher(line);
                if (m.find()) headLineNum = Integer.parseInt(m.group(1)) - 1;
            } else if (currentFile != null) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    headLineNum++;
                    result.get(currentFile).add(headLineNum);
                } else if (!line.startsWith("-")) {
                    headLineNum++;
                }
            }
        }
        return result;
    }

    public int findNearestDiffLine(Set<Integer> validLines, int target) {
        return validLines.stream()
                .min(Comparator.comparingInt(l -> Math.abs(l - target)))
                .orElse(target);
    }
}