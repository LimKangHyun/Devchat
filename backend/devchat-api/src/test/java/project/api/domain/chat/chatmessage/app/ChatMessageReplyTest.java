package project.api.domain.chat.chatmessage.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import project.api.auth.dto.MemberDetails;
import project.api.domain.chat.chatmessage.dao.ChatMessageIndexStatusRepository;
import project.api.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.api.domain.chat.chatmessage.dto.ChatMessageRequest;
import project.api.domain.chat.chatmessage.dto.ReplyPreview;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatmessage.entity.MessageType;
import project.api.domain.chat.chatmessage.entity.ChatMessageIndexStatus;
import project.api.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.api.domain.chat.chatroom.app.ChatRoomParticipantService;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.chat.chatsearch.dao.ChatMessageSearchRepository;
import project.api.domain.imagefile.ImageFileService;
import project.api.domain.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class ChatMessageReplyTest {

    @InjectMocks private ChatMessageService chatMessageService;

    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private ChatMessageSearchRepository chatMessageSearchRepository;
    @Mock private ChatMessageIndexStatusRepository chatMessageIndexStatusRepository;
    @Mock private ChatRoomParticipantService chatRoomParticipantService;
    @Mock private ImageFileService imageFileService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EntityManager entityManager;
    @Mock private ChatMessageMapper messageMapper;

    private final MemberDetails me = mock(MemberDetails.class);
    private final Member sender = mock(Member.class);
    private ChatRoom room10;
    private ChatMessage newMessage;

    @BeforeEach
    void setUp() {
        room10 = roomOf(10L);
        newMessage = ChatMessage.builder().chatRoom(room10).type(MessageType.TEXT).content("응 맞아").build();
        lenient().when(me.getId()).thenReturn(1L);
        lenient().when(entityManager.getReference(Member.class, 1L)).thenReturn(sender);
        lenient().when(entityManager.getReference(ChatRoom.class, 10L)).thenReturn(room10);
        lenient().when(messageMapper.toEntityWithText(any(), any(), any())).thenReturn(newMessage);
        lenient().when(messageMapper.toIndexStatus(any())).thenReturn(mock(ChatMessageIndexStatus.class));
    }

    private static ChatRoom roomOf(Long id) {
        ChatRoom room = mock(ChatRoom.class);
        lenient().when(room.getId()).thenReturn(id);
        return room;
    }

    private ChatMessageRequest replyRequest(Long replyToId) {
        ChatMessageRequest req = new ChatMessageRequest();
        req.setType(MessageType.TEXT);
        req.setContent("응 맞아");
        req.setReplyToId(replyToId);
        return req;
    }

    private ChatMessage original(Long id, ChatRoom room, String content) {
        Member author = mock(Member.class);
        lenient().when(author.getId()).thenReturn(2L);
        lenient().when(author.getNickname()).thenReturn("강현");
        return ChatMessage.builder().id(id).sender(author).chatRoom(room).type(MessageType.TEXT).content(content).build();
    }

    @Test
    void 같은_방_메시지에는_답장한_순간의_원문을_저장한다() {
        ChatMessage original = original(5L, room10, "이거 맞아?");
        given(chatMessageRepository.findById(5L)).willReturn(Optional.of(original));

        chatMessageService.save(10L, replyRequest(5L), me);

        assertThat(ReplyPreview.of(newMessage))
                .isEqualTo(new ReplyPreview(5L, 2L, "강현", MessageType.TEXT, "이거 맞아?"));
    }

    @Test
    void 원문을_나중에_고치거나_지워도_답장의_원문은_그대로다() {
        ChatMessage original = original(5L, room10, "이거 맞아?");
        given(chatMessageRepository.findById(5L)).willReturn(Optional.of(original));
        chatMessageService.save(10L, replyRequest(5L), me);

        original.updateContent("고친 내용");
        original.delete();

        assertThat(ReplyPreview.of(newMessage).preview()).isEqualTo("이거 맞아?");
    }

    @Test
    void 다른_방_메시지에는_답장을_달지_않는다() {
        // 다른 방 메시지 내용이 이 방에 미리보기로 새면 안 된다
        ChatMessage secret = original(7L, roomOf(99L), "비밀");
        given(chatMessageRepository.findById(7L)).willReturn(Optional.of(secret));

        chatMessageService.save(10L, replyRequest(7L), me);

        assertThat(ReplyPreview.of(newMessage)).isNull();
    }

    @Test
    void 이미_지운_메시지나_없는_메시지면_보통_메시지로_저장한다() {
        ChatMessage deleted = original(6L, room10, "지운 글");
        deleted.delete();
        given(chatMessageRepository.findById(6L)).willReturn(Optional.of(deleted));
        given(chatMessageRepository.findById(8L)).willReturn(Optional.empty());

        chatMessageService.save(10L, replyRequest(6L), me);
        assertThat(ReplyPreview.of(newMessage)).isNull();

        chatMessageService.save(10L, replyRequest(8L), me);
        assertThat(ReplyPreview.of(newMessage)).isNull();
    }

    @Test
    void 미리보기는_한_줄로_줄이고_이미지_코드는_표시만_한다() {
        ChatMessage longText = ChatMessage.builder().id(1L).type(MessageType.TEXT).content("가".repeat(100) + "\n둘째 줄").build();
        ChatMessage code = ChatMessage.builder().id(2L).type(MessageType.CODE).content("int a = 1;\nint b = 2;").build();
        ChatMessage image = ChatMessage.builder().id(3L).type(MessageType.IMAGE).build();

        assertThat(ReplyPreview.previewOf(longText)).hasSize(81).endsWith("…");
        assertThat(ReplyPreview.previewOf(code)).isEqualTo("[코드] int a = 1;");
        assertThat(ReplyPreview.previewOf(image)).isEqualTo("[이미지]");
        assertThat(ReplyPreview.of(null)).isNull();
    }
}
