package project.backend.domain.community.entity;

import jakarta.persistence.*;
import lombok.*;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.member.entity.Member;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int maxCount;

    @Builder.Default
    private int currentCount = 1;

    private String tag;

    private String mode;

    private LocalDate deadline;

    private long viewCount = 0;

    private boolean closed = false;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private ChatRoom chatRoom;

    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member author;

    private String techStacks;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Applicant> applicants = new ArrayList<>();

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void update(String title, String content, Integer maxCount,
                       String tag, String mode, LocalDate deadline, String techStacks) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (maxCount != null) this.maxCount = maxCount;
        if (tag != null) this.tag = tag;
        if (mode != null) this.mode = mode;
        if (deadline != null) this.deadline = deadline;
        if (techStacks != null) this.techStacks = techStacks;
    }

    public Long getChatRoomId() {
        return chatRoom.getId();
    }

    public void close() {
        this.closed = true;
    }

    public void incrementCurrentCount() {
        this.currentCount++;
    }

    public boolean isFull() {
        return this.currentCount >= this.maxCount;
    }
}