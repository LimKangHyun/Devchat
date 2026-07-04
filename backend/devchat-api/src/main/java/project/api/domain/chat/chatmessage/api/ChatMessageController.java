package project.api.domain.chat.chatmessage.api;

import java.security.Principal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import project.api.auth.dto.MemberDetails;
import project.api.domain.chat.chatmessage.app.ChatMessageService;
import project.api.domain.chat.chatmessage.dto.ChatMessageEditRequest;
import project.api.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.api.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.api.domain.chat.chatmessage.dto.ChatScrollResponse;
import project.api.domain.imagefile.ImageFile;
import project.api.domain.imagefile.ImageFileService;

@Tag(name = "Chat Message", description = "채팅 메시지 API")
@Validated
@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final ImageFileService imageFileService;
    private final ChatMessageService chatMessageService;

    @MessageMapping("/send-message/{roomId}")
    public void sendMessage(@DestinationVariable Long roomId,
                            @Payload ChatMessageRequest request, Principal principal) {

        MemberDetails userDetails = (MemberDetails) ((Authentication) principal).getPrincipal();
        chatMessageService.save(roomId, request, userDetails);
    }

    @Operation(summary = "메시지 저장 - TEST")
    @PostMapping("/chat-rooms/{roomId}/messages")
    public void saveMessage(@PathVariable Long roomId,
        @RequestBody ChatMessageRequest request, Principal principal) {
        MemberDetails userDetails = (MemberDetails) ((Authentication) principal).getPrincipal();
        chatMessageService.save(roomId, request, userDetails);
    }

    @MessageMapping("/edit-message/{roomId}")
    public void editMessage(@DestinationVariable Long roomId, @Payload
    ChatMessageEditRequest request, Principal principal) {
        MemberDetails userDetails = (MemberDetails) ((Authentication) principal).getPrincipal();
        chatMessageService.editMessage(roomId, request, userDetails);
    }

    @Operation(summary = "메시지 검색")
    @GetMapping("/chat/search/{roomId}")
    public Slice<ChatMessageSearchResponse> searchMessages(
            @AuthenticationPrincipal MemberDetails memberDetails,
            @PathVariable Long roomId,
            @RequestParam @NotBlank(message = "검색어를 입력해주세요.") String keyword,
            @RequestParam(required = false) Long lastMessageId,
            @RequestParam(defaultValue = "10") @Min(value = 1) int pageSize) {

        return chatMessageService.searchMessages(memberDetails.getId(), roomId, keyword, lastMessageId, pageSize);
    }

    @Operation(summary = "메시지 스크롤 조회")
    @GetMapping("/{roomId}/messages")
    public ChatScrollResponse getMessages(
        @AuthenticationPrincipal MemberDetails memberDetails,
        @PathVariable Long roomId,
        @RequestParam(required = false) Long cursor,
        @RequestParam(defaultValue = "30") int size
    ) {
        return chatMessageService.getMessagesByRoomId(memberDetails.getId(), roomId, cursor, size);
    }

    @MessageMapping("/delete-message/{roomId}")
    public void deleteMessage(@DestinationVariable Long roomId,
        @Payload Long messageId, Principal principal) {

        MemberDetails userDetails = (MemberDetails) ((Authentication) principal).getPrincipal();
        chatMessageService.deleteMessage(roomId, messageId,
                userDetails);
    }

    @Operation(summary = "이미지 업로드")
    @PostMapping("/send-image")
    public Long uploadImage(@RequestParam MultipartFile image) {
        ImageFile imageFile = imageFileService.saveChatImage(image);
        return imageFile.getImageId();
    }

}
