package project.backend.domain.chat.chatmessage.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.app.ChatMessageService;
import project.backend.domain.chat.chatmessage.dto.ChatMessageEditRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.backend.domain.chat.chatmessage.dto.ChatScrollResponse;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.imagefile.ImageFileService;

@RestController
@RequiredArgsConstructor
public class ChatMessageController {

    private final ImageFileService imageFileService;
    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/send-message/{roomId}") //클라이언트가 메세지를 보낼 경로
    public ChatMessageResponse sendMessage(@DestinationVariable Long roomId,
        @Payload ChatMessageRequest request, Principal principal) {

        MemberDetails userDetails = (MemberDetails) ((Authentication) principal).getPrincipal();
        Long memberId = userDetails.getId();
        ChatMessageResponse response = chatMessageService.save(roomId, request, memberId);

        messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);

        return response;
    }

    @PostMapping("/chat-rooms/{roomId}/messages")
    public ChatMessageResponse saveMessage(@PathVariable Long roomId,
        @RequestBody ChatMessageRequest request, Principal principal) {
        MemberDetails userDetails = (MemberDetails) ((Authentication) principal).getPrincipal();
        Long memberId = userDetails.getId();
        return chatMessageService.save(roomId, request, memberId);
    }

    @MessageMapping("/edit-message/{roomId}")
    public ChatMessageResponse editMessage(@DestinationVariable Long roomId, @Payload
    ChatMessageEditRequest request, Principal principal) {
        ChatMessageResponse response = chatMessageService.editMessage(roomId, request,
            principal.getName());

        messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);

        return response;
    }

    @GetMapping("/chat/search/{roomId}")
    public Slice<ChatMessageSearchResponse> searchMessages(
        @AuthenticationPrincipal MemberDetails memberDetails,
        @PathVariable("roomId") Long roomId,
        @RequestParam("keyword") String keyword,
        @RequestParam(required = false) Long lastMessageId,
        @RequestParam(defaultValue = "10") int size
    ) throws JsonProcessingException {  // writeValueAsString 때문에 예외 선언 필요
        ChatMessageSearchRequest request = ChatMessageSearchRequest.of(keyword, lastMessageId,
            size);
        Slice<ChatMessageSearchResponse> result = chatMessageService.searchMessages(
            memberDetails.getId(), roomId, request);

        return result;
    }

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
    public ChatMessageResponse deleteMessage(@DestinationVariable Long roomId,
        @Payload Long messageId,
        Principal principal) {
        ChatMessageResponse response = chatMessageService.deleteMessage(roomId, messageId,
            principal.getName());

        messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);

        return response;
    }

    @PostMapping("/send-image")
    public Long uploadImage(@RequestParam MultipartFile image) {
        ImageFile imageFile = imageFileService.saveChatImage(image);
        return imageFile.getImageId();
    }

}
