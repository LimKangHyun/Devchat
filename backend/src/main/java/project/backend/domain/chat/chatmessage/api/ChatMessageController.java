package project.backend.domain.chat.chatmessage.api;

import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import project.backend.domain.chat.chatmessage.app.ChatMessageService;
import project.backend.domain.chat.chatmessage.dto.ChatMessageEditRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.imagefile.ImageFileService;
import project.backend.domain.imagefile.ImageType;
import project.backend.global.exception.errorcode.AuthErrorCode;
import project.backend.global.exception.ex.AuthException;
import project.backend.global.security.dto.MemberDetails;

@RestController
@RequiredArgsConstructor
public class ChatMessageController {

	private final ChatMessageService chatMessageService;
	private final ImageFileService imageFileService;
	private final SimpMessagingTemplate messagingTemplate;

	@MessageMapping("/send-message/{roomId}") //클라이언트가 메세지를 보낼 경로
	public ChatMessageResponse sendMessage(@DestinationVariable Long roomId,
		@Payload ChatMessageRequest request, Principal principal) {
		ChatMessageResponse response = chatMessageService.save(roomId, request,
			principal.getName());

		messagingTemplate.convertAndSend("/topic/chat/" + roomId, response);

		return response;
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
	public Page<ChatMessageSearchResponse> searchMessages(
		@PathVariable("roomId") Long roomId,
		@RequestParam("keyword") String keyword,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "10") int size
	) {
		ChatMessageSearchRequest request = ChatMessageSearchRequest.of(keyword, page, size);

		return chatMessageService.searchMessages(roomId, request);
	}

	@GetMapping("/{roomId}/messages")
	public List<ChatMessageResponse> getMessages(@PathVariable Long roomId,
		@AuthenticationPrincipal MemberDetails memberDetails) {

		if (memberDetails == null) {
			throw new AuthException(AuthErrorCode.UNAUTHORIZED_USER);
		}

		return chatMessageService.getMessagesByRoomId(roomId, memberDetails.getId());
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
		ImageFile imageFile = imageFileService.saveImageFile(image, ImageType.CHAT_IMAGE);
		return imageFile.getImageId();
	}

}
