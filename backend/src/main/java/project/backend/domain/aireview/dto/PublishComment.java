package project.backend.domain.aireview.dto;

public record PublishComment(
        String path,
        int line,
        String body
) {}