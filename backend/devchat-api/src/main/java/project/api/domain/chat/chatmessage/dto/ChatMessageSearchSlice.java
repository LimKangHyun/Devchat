package project.api.domain.chat.chatmessage.dto;

import java.util.List;
import lombok.Getter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

@Getter
public class ChatMessageSearchSlice extends SliceImpl<ChatMessageSearchResponse> {

    private final Long totalCount;

    public ChatMessageSearchSlice(List<ChatMessageSearchResponse> content,
        Pageable pageable,
        boolean hasNext,
        Long totalCount) {
        super(content, pageable, hasNext);
        this.totalCount = totalCount;
    }
}