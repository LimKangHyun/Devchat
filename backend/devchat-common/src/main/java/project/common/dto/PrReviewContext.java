package project.common.dto;

import project.common.dto.github.GitRepoDto;

public record PrReviewContext(
        GitRepoDto repo,
        String fullDiff,
        String headSha,
        String baseSha,
        Long repoId
) {}