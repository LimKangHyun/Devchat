package project.backend.domain.aireview.dto;

import project.backend.domain.github.dto.GitRepoDto;

public record PrReviewContext(
    GitRepoDto repo,
    String fullDiff,
    String headSha,
    String baseSha,
    Long repoId
) {}
