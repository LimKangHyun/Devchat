package project.backend.domain.aireview.app;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
}