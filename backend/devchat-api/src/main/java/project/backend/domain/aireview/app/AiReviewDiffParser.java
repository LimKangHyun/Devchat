package project.backend.domain.aireview.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import project.common.dto.InlineReview;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AiReviewDiffParser {

    public List<String> parseChangedFiles(String diff) {
        return Arrays.stream(diff.split("\n"))
                .filter(line -> line.startsWith("diff --git"))
                .map(line -> line.split(" b/")[1])
                .collect(Collectors.toList());
    }

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