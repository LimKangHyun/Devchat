package project.backend.domain.chat.chatmessage.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static project.backend.domain.chat.chatmessage.entity.MessageType.CODE;
import static project.backend.domain.chat.chatmessage.entity.MessageType.IMAGE;
import static project.backend.domain.chat.chatmessage.entity.MessageType.TEXT;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatsearch.dao.ChatMessageSearchRepository;
import project.backend.domain.chat.chatmessage.dto.ChatMessageEditRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.backend.domain.chat.chatmessage.dto.ChatMessageResponse;
import project.backend.domain.chat.chatmessage.dto.ChatMessageSearchResponse;
import project.backend.domain.chat.chatmessage.dto.ChatScrollResponse;
import project.backend.domain.chat.chatmessage.dto.event.ChatMessageSavedEvent;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatsearch.entity.ChatMessageSearch;
import project.backend.domain.chat.chatmessage.entity.ChatMessageIndexStatus;
import project.backend.domain.chat.chatmessage.dao.ChatMessageIndexStatusRepository;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.app.ChatRoomParticipantService;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.imagefile.ImageFile;
import project.backend.domain.imagefile.ImageFileService;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.ex.AuthException;
import project.backend.global.exception.ex.ChatMessageException;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatMessageSearchRepository chatMessageSearchRepository;
    @Mock private ChatMessageIndexStatusRepository chatMessageIndexStatusRepository;
    @Mock private ChatRoomParticipantService chatRoomParticipantService;
    @Mock private ImageFileService imageFileService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EntityManager entityManager;
    @Mock private ChatMessageMapper messageMapper;

    private Member sender;
    private ChatRoom chatRoom;
    private ChatMessage textMessage;
    private ChatMessage imageMessage;
    private ChatMessage codeMessage;
    private MemberDetails memberDetails;

    @BeforeEach
    void setUp() {
        sender = mock(Member.class);
        chatRoom = mock(ChatRoom.class);
        textMessage = mock(ChatMessage.class);
        imageMessage = mock(ChatMessage.class);
        codeMessage = mock(ChatMessage.class);
        memberDetails = mock(MemberDetails.class);
    }

    @Nested
    @DisplayName("save() - 메시지 저장")
    class Save {

        @Test
        @DisplayName("TEXT 메시지 저장 시 이벤트가 발행된다")
        void save_textMessage_success() {
            given(memberDetails.getId()).willReturn(1L);

            ChatMessageRequest request = mock(ChatMessageRequest.class);
            given(request.getType()).willReturn(TEXT);

            given(entityManager.getReference(Member.class, 1L)).willReturn(sender);
            given(entityManager.getReference(ChatRoom.class, 10L)).willReturn(chatRoom);
            given(messageMapper.toEntityWithText(chatRoom, sender, request)).willReturn(textMessage);
            given(textMessage.getType()).willReturn(TEXT);
            given(textMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(messageMapper.toIndexStatus(textMessage)).willReturn(mock(ChatMessageIndexStatus.class));

            chatMessageService.save(10L, request, memberDetails);

            then(chatMessageRepository).should().save(textMessage);
            then(eventPublisher).should().publishEvent(any(ChatMessageSavedEvent.class));
            then(chatMessageIndexStatusRepository).should().save(any(ChatMessageIndexStatus.class));
        }

        @Test
        @DisplayName("IMAGE 메시지를 저장하면 색인 상태가 저장되지 않는다")
        void save_imageMessage_noIndexStatus() {
            given(memberDetails.getId()).willReturn(1L);

            ChatMessageRequest request = mock(ChatMessageRequest.class);
            given(request.getType()).willReturn(IMAGE);
            given(request.getImageFileId()).willReturn(99L);

            given(entityManager.getReference(Member.class, 1L)).willReturn(sender);
            given(entityManager.getReference(ChatRoom.class, 10L)).willReturn(chatRoom);

            ImageFile imageFile = mock(ImageFile.class);
            given(imageFileService.getImageById(99L)).willReturn(imageFile);
            given(messageMapper.toEntityWithImage(chatRoom, sender, imageFile)).willReturn(imageMessage);
            given(imageMessage.getType()).willReturn(IMAGE);
            given(imageMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);

            chatMessageService.save(10L, request, memberDetails);

            then(chatMessageIndexStatusRepository).should(never()).save(any());
            then(eventPublisher).should().publishEvent(any(ChatMessageSavedEvent.class));
        }

        @Test
        @DisplayName("CODE 메시지를 저장하면 이벤트가 발행된다")
        void save_codeMessage_publishesEvent() {
            given(memberDetails.getId()).willReturn(1L);

            ChatMessageRequest request = mock(ChatMessageRequest.class);
            given(request.getType()).willReturn(CODE);

            given(entityManager.getReference(Member.class, 1L)).willReturn(sender);
            given(entityManager.getReference(ChatRoom.class, 10L)).willReturn(chatRoom);
            given(messageMapper.toEntityWithCode(chatRoom, sender, request)).willReturn(codeMessage);
            given(codeMessage.getType()).willReturn(CODE);
            given(codeMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(messageMapper.toIndexStatus(codeMessage)).willReturn(mock(ChatMessageIndexStatus.class));

            chatMessageService.save(10L, request, memberDetails);

            then(eventPublisher).should().publishEvent(any(ChatMessageSavedEvent.class));
            then(chatMessageIndexStatusRepository).should().save(any(ChatMessageIndexStatus.class));
        }
    }

    @Nested
    @DisplayName("searchMessages() - 메시지 검색")
    class SearchMessages {

        @Test
        @DisplayName("첫 페이지 검색 시 totalCount가 포함된 결과를 반환한다")
        void searchMessages_firstPage_returnsTotalCount() {
            List<Long> ids = new ArrayList<>(List.of(1L, 2L, 3L));
            given(chatMessageSearchRepository.searchIdsByKeywordAndRoomIdWithCursor(
                    "hello", 10L, null, 11)).willReturn(ids);
            given(chatMessageSearchRepository.countByKeywordAndRoomId("hello", 10L)).willReturn(3L);

            ChatMessage msg1 = mock(ChatMessage.class);
            given(msg1.getId()).willReturn(1L);
            ChatMessage msg2 = mock(ChatMessage.class);
            given(msg2.getId()).willReturn(2L);
            ChatMessage msg3 = mock(ChatMessage.class);
            given(msg3.getId()).willReturn(3L);

            given(chatMessageRepository.findByIdIn(ids)).willReturn(List.of(msg1, msg2, msg3));
            given(messageMapper.toSearchResponse(any())).willReturn(mock(ChatMessageSearchResponse.class));
            willDoNothing().given(chatRoomParticipantService).validateParticipant(1L, 10L);

            Slice<ChatMessageSearchResponse> result = chatMessageService.searchMessages(
                    1L, 10L, "hello", null, 10);

            assertThat(result.getContent()).hasSize(3);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("다음 페이지가 있을 때 hasNext=true를 반환한다")
        void searchMessages_hasNextPage_returnsHasNextTrue() {
            List<Long> ids = new ArrayList<>(List.of(10L, 20L, 30L));
            given(chatMessageSearchRepository.searchIdsByKeywordAndRoomIdWithCursor(
                    "hello", 10L, 50L, 3)).willReturn(ids);

            ChatMessage msg1 = mock(ChatMessage.class);
            given(msg1.getId()).willReturn(10L);
            ChatMessage msg2 = mock(ChatMessage.class);
            given(msg2.getId()).willReturn(20L);

            given(chatMessageRepository.findByIdIn(anyList())).willReturn(List.of(msg1, msg2));
            given(messageMapper.toSearchResponse(any())).willReturn(mock(ChatMessageSearchResponse.class));
            willDoNothing().given(chatRoomParticipantService).validateParticipant(1L, 10L);

            Slice<ChatMessageSearchResponse> result = chatMessageService.searchMessages(
                    1L, 10L, "hello", 50L, 2);

            assertThat(result.hasNext()).isTrue();
            assertThat(result.getContent()).hasSize(2);
            then(chatMessageSearchRepository).should(never()).countByKeywordAndRoomId(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("editMessage() - 메시지 수정")
    class EditMessage {

        @Test
        @DisplayName("본인 TEXT 메시지를 정상적으로 수정한다")
        void editMessage_textMessage_success() {
            given(textMessage.getId()).willReturn(100L);
            given(textMessage.getSender()).willReturn(sender);
            given(textMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(textMessage.getType()).willReturn(TEXT);
            given(textMessage.getContent()).willReturn("hello");
            given(sender.getId()).willReturn(1L);
            given(memberDetails.getId()).willReturn(1L);

            given(chatMessageRepository.findById(100L)).willReturn(Optional.of(textMessage));
            ChatMessageSearch searchEntity = mock(ChatMessageSearch.class);
            given(chatMessageSearchRepository.findById(100L)).willReturn(Optional.of(searchEntity));

            chatMessageService.editMessage(10L,
                    new ChatMessageEditRequest(100L, "updated", TEXT, null), memberDetails);

            then(textMessage).should().updateContent("updated");
            then(searchEntity).should().updateContent("hello");
        }

        @Test
        @DisplayName("CODE 메시지 수정 시 언어도 함께 업데이트한다")
        void editMessage_codeMessage_updatesLanguage() {
            given(codeMessage.getId()).willReturn(300L);
            given(codeMessage.getSender()).willReturn(sender);
            given(codeMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(codeMessage.getType()).willReturn(CODE);
            given(codeMessage.getContent()).willReturn("System.out.println();");
            given(sender.getId()).willReturn(1L);
            given(memberDetails.getId()).willReturn(1L);

            given(chatMessageRepository.findById(300L)).willReturn(Optional.of(codeMessage));
            ChatMessageSearch searchEntity = mock(ChatMessageSearch.class);
            given(chatMessageSearchRepository.findById(300L)).willReturn(Optional.of(searchEntity));

            chatMessageService.editMessage(10L,
                    new ChatMessageEditRequest(300L, "int x=0;", CODE, "JAVA"), memberDetails);

            then(codeMessage).should().updateContent("int x=0;");
            then(codeMessage).should().updateLanguage("JAVA");
        }

        @Test
        @DisplayName("존재하지 않는 메시지 수정 시 예외를 던진다")
        void editMessage_notFound_throwsException() {
            given(chatMessageRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    chatMessageService.editMessage(10L,
                            new ChatMessageEditRequest(999L, "x", TEXT, null), memberDetails))
                    .isInstanceOf(ChatMessageException.class);
        }

        @Test
        @DisplayName("본인이 아닌 사람이 메시지를 수정하면 FORBIDDEN 예외를 던진다")
        void editMessage_notOwner_throwsAuthException() {
            Member otherSender = mock(Member.class);
            given(otherSender.getId()).willReturn(99L);
            given(textMessage.getSender()).willReturn(otherSender);
            given(textMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(memberDetails.getId()).willReturn(1L);

            given(chatMessageRepository.findById(100L)).willReturn(Optional.of(textMessage));

            assertThatThrownBy(() ->
                    chatMessageService.editMessage(10L,
                            new ChatMessageEditRequest(100L, "hack", TEXT, null), memberDetails))
                    .isInstanceOf(AuthException.class);
        }

        @Test
        @DisplayName("IMAGE 메시지는 검색 엔티티 업데이트를 하지 않는다")
        void editMessage_imageMessage_noSearchUpdate() {
            given(imageMessage.getSender()).willReturn(sender);
            given(imageMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(imageMessage.getType()).willReturn(IMAGE);
            given(sender.getId()).willReturn(1L);
            given(memberDetails.getId()).willReturn(1L);

            given(chatMessageRepository.findById(200L)).willReturn(Optional.of(imageMessage));

            chatMessageService.editMessage(10L,
                    new ChatMessageEditRequest(200L, "", IMAGE, null), memberDetails);

            then(chatMessageSearchRepository).should(never()).findById(anyLong());
        }
    }

    @Nested
    @DisplayName("deleteMessage() - 메시지 삭제")
    class DeleteMessage {

        @Test
        @DisplayName("본인 TEXT 메시지를 정상적으로 삭제한다")
        void deleteMessage_textMessage_success() {
            given(textMessage.getId()).willReturn(100L);
            given(textMessage.getSender()).willReturn(sender);
            given(textMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(textMessage.getType()).willReturn(TEXT);
            given(sender.getId()).willReturn(1L);
            given(memberDetails.getId()).willReturn(1L);

            given(chatMessageRepository.findById(100L)).willReturn(Optional.of(textMessage));
            ChatMessageSearch searchEntity = mock(ChatMessageSearch.class);
            given(chatMessageSearchRepository.findById(100L)).willReturn(Optional.of(searchEntity));

            chatMessageService.deleteMessage(10L, 100L, memberDetails);

            then(textMessage).should().delete();
            then(searchEntity).should().deleteContent();
        }

        @Test
        @DisplayName("존재하지 않는 메시지 삭제 시 예외를 던진다")
        void deleteMessage_notFound_throwsException() {
            given(chatMessageRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> chatMessageService.deleteMessage(10L, 999L, memberDetails))
                    .isInstanceOf(ChatMessageException.class);
        }

        @Test
        @DisplayName("본인이 아닌 사람이 메시지를 삭제하면 FORBIDDEN 예외를 던진다")
        void deleteMessage_notOwner_throwsAuthException() {
            Member otherSender = mock(Member.class);
            given(otherSender.getId()).willReturn(99L);
            given(imageMessage.getSender()).willReturn(otherSender);
            given(imageMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(memberDetails.getId()).willReturn(1L);

            given(chatMessageRepository.findById(200L)).willReturn(Optional.of(imageMessage));

            assertThatThrownBy(() -> chatMessageService.deleteMessage(10L, 200L, memberDetails))
                    .isInstanceOf(AuthException.class);
        }

        @Test
        @DisplayName("IMAGE 메시지 삭제 시 검색 엔티티를 건드리지 않는다")
        void deleteMessage_imageMessage_noSearchDelete() {
            given(imageMessage.getSender()).willReturn(sender);
            given(imageMessage.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(imageMessage.getType()).willReturn(IMAGE);
            given(sender.getId()).willReturn(1L);
            given(memberDetails.getId()).willReturn(1L);

            given(chatMessageRepository.findById(200L)).willReturn(Optional.of(imageMessage));

            chatMessageService.deleteMessage(10L, 200L, memberDetails);

            then(imageMessage).should().delete();
            then(chatMessageSearchRepository).should(never()).findById(anyLong());
        }
    }

    @Nested
    @DisplayName("getMessagesByRoomId() - 스크롤 페이징 조회")
    class GetMessagesByRoomId {

        @Test
        @DisplayName("cursor가 null이면 최신 메시지부터 조회한다")
        void getMessages_noCursor_fetchFromLatest() {
            willDoNothing().given(chatRoomParticipantService).validateParticipant(1L, 10L);
            given(chatMessageRepository.findByChatRoom_IdOrderByIdDesc(eq(10L), any(PageRequest.class)))
                    .willReturn(List.of(textMessage));
            given(messageMapper.toResponse(textMessage)).willReturn(mock(ChatMessageResponse.class));

            ChatScrollResponse result = chatMessageService.getMessagesByRoomId(1L, 10L, null, 10);

            assertThat(result.getMessages()).hasSize(1);
            assertThat(result.getNextCursor()).isNull();
            then(chatMessageRepository).should(never())
                    .findByChatRoom_IdAndIdLessThanOrderByIdDesc(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("cursor가 있으면 해당 cursor 이전 메시지를 조회한다")
        void getMessages_withCursor_fetchBeforeCursor() {
            willDoNothing().given(chatRoomParticipantService).validateParticipant(1L, 10L);
            given(chatMessageRepository.findByChatRoom_IdAndIdLessThanOrderByIdDesc(
                    eq(10L), eq(50L), any(PageRequest.class)))
                    .willReturn(List.of(textMessage));
            given(messageMapper.toResponse(textMessage)).willReturn(mock(ChatMessageResponse.class));

            ChatScrollResponse result = chatMessageService.getMessagesByRoomId(1L, 10L, 50L, 10);

            assertThat(result.getMessages()).hasSize(1);
            then(chatMessageRepository).should(never())
                    .findByChatRoom_IdOrderByIdDesc(anyLong(), any());
        }

        @Test
        @DisplayName("size+1개가 조회되면 nextCursor를 반환한다")
        void getMessages_hasMore_returnsNextCursor() {
            willDoNothing().given(chatRoomParticipantService).validateParticipant(1L, 10L);

            ChatMessage msg1 = mock(ChatMessage.class);
            ChatMessage msg2 = mock(ChatMessage.class);
            ChatMessage msg3 = mock(ChatMessage.class);
            given(msg3.getId()).willReturn(30L);

            given(chatMessageRepository.findByChatRoom_IdOrderByIdDesc(eq(10L), any(PageRequest.class)))
                    .willReturn(List.of(msg1, msg2, msg3));
            given(messageMapper.toResponse(any())).willReturn(mock(ChatMessageResponse.class));

            ChatScrollResponse result = chatMessageService.getMessagesByRoomId(1L, 10L, null, 2);

            assertThat(result.getMessages()).hasSize(2);
            assertThat(result.getNextCursor()).isNotNull();
        }
    }
}