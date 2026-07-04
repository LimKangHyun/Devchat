package project.api.domain.aireview.dto;

public record PublishComment(
        String path,
        int line,
        String body
) {}