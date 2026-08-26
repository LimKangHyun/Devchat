package project.ai.indexing.structural;

/**
 * diff가 건드린 하나의 코드 심볼과 그 변경 종류.
 * 영향 분석의 출발점이 된다 — "무엇이 바뀌었나"에 따라 검색 범위가 정해진다.
 */
public record ChangedSymbol(Kind kind, String name) {

    public enum Kind {
        /** 필드 선언 변경 → 이 클래스를 참조하는 코드가 영향받는다. */
        FIELD,
        /** 메서드 시그니처 변경 → 호출자 + 구현체 + 오버라이드한 자식 전부. */
        METHOD_SIGNATURE,
        /** 메서드 본문만 변경 → 동작이 바뀌므로 호출자가 영향받는다. */
        METHOD_BODY,
        /** 클래스 선언부(extends/implements) 변경 → 구현체·자식 전부. */
        CLASS_HEADER
    }
}