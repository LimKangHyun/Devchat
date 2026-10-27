package project.api.domain.github.event;

/**
 * 기본 브랜치로 PR이 머지됨.
 *
 * @param mergeCommitSha 머지 결과 커밋. PR 브랜치의 마지막 커밋(head)이 아니라 이 커밋 기준으로 파일을 읽어야
 *                       머지 전에 기본 브랜치에 먼저 들어간 변경까지 반영된다.
 */
public record PrMergedEvent(Long roomId, int prNumber, String mergeCommitSha) {}
