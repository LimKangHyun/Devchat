package project.backend.domain.aireview.entity;

public enum PrStatus {
    OPEN, CLOSED, MERGED, EDITED, REOPENED;

    public static PrStatus from(String action, boolean merged) {
        return switch (action) {
            case "opened" -> OPEN;
            case "closed" -> merged ? MERGED : CLOSED;
            case "reopened" -> REOPENED;
            case "edited" -> EDITED;
            default -> throw new IllegalArgumentException("Unknown PR action: " + action);
        };
    }
}