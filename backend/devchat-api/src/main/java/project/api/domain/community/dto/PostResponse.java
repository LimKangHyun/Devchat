package project.api.domain.community.dto;

import lombok.Builder;
import lombok.Getter;
import project.api.domain.community.entity.Post;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Getter
@Builder
public class PostResponse {

    private Long id;
    private String title;
    private String content;
    private int maxCount;
    private int currentCount;
    private String tag;
    private String mode;
    private LocalDate deadline;
    private long viewCount;
    private boolean closed;
    private LocalDateTime createdAt;
    private String authorNickname;
    private String authorProfileImg;
    private List<String> techStacks;

    public static PostResponse from(Post post) {

        List<String> techStacks = List.of();
        if (post.getTechStacks() != null && !post.getTechStacks().isBlank()) {
            techStacks = Arrays.stream(post.getTechStacks().split(","))
                    .map(String::trim)
                    .toList();
        }

        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .maxCount(post.getMaxCount())
                .currentCount(post.getCurrentCount())
                .tag(post.getTag())
                .mode(post.getMode())
                .deadline(post.getDeadline())
                .viewCount(post.getViewCount())
                .closed(post.isClosed())
                .createdAt(post.getCreatedAt())
                .authorNickname(post.getAuthor().getNickname())
                .authorProfileImg(post.getAuthor().getProfileImage())
                .techStacks(techStacks)
                .build();
    }
}