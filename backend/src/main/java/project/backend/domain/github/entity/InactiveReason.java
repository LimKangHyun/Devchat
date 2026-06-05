package project.backend.domain.github.entity;

public enum InactiveReason {
    DUPLICATE,      // 중복
    UNNECESSARY,    // 불필요
    INCORRECT,      // 잘못됨
    OTHER           // 기타
}