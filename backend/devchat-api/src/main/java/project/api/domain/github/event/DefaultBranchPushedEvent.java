package project.api.domain.github.event;

/**
 * 기본 브랜치에 직접 push됨 (PR 머지로 생긴 push는 PrMergedEvent로 처리하므로 여기서는 걸러진다).
 *
 * @param before push 전 커밋
 * @param after  push 후 커밋. 이 커밋 기준으로 파일을 읽는다
 */
public record DefaultBranchPushedEvent(Long roomId, String before, String after) {}
