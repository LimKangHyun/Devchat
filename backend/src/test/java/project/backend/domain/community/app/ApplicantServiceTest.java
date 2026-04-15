package project.backend.domain.community.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

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
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.app.ChatMessageService;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatroom.app.ChatRoomAlarmService;
import project.backend.domain.chat.chatroom.app.ChatRoomCacheService;
import project.backend.domain.chat.chatroom.app.ChatRoomSequenceService;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.community.dao.ApplicantRepository;
import project.backend.domain.community.dao.PostRepository;
import project.backend.domain.community.dto.ApplicantResponse;
import project.backend.domain.community.entity.Applicant;
import project.backend.domain.community.entity.ApplicantStatus;
import project.backend.domain.community.entity.Post;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.ex.ChatRoomException;
import project.backend.global.exception.ex.PostException;

@ExtendWith(MockitoExtension.class)
class ApplicantServiceTest {

    @InjectMocks
    private ApplicantService applicantService;

    @Mock private PostRepository postRepository;
    @Mock private ApplicantRepository applicantRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ChatMessageService chatMessageService;
    @Mock private ChatRoomCacheService chatRoomCacheService;
    @Mock private ChatRoomSequenceService chatRoomSequenceService;
    @Mock private ChatRoomAlarmService chatRoomAlarmService;

    private Post post;
    private Member author;
    private Member applicantMember;
    private ChatRoom chatRoom;
    private MemberDetails ownerDetails;
    private MemberDetails otherDetails;

    @BeforeEach
    void setUp() {
        author = mock(Member.class);
        applicantMember = mock(Member.class);
        chatRoom = mock(ChatRoom.class);
        post = mock(Post.class);
        ownerDetails = mock(MemberDetails.class);
        otherDetails = mock(MemberDetails.class);
    }

    @Nested
    @DisplayName("getApplicants() - 신청자 목록 조회")
    class GetApplicants {

        @Test
        @DisplayName("방장이 신청자 목록을 정상 조회한다")
        void getApplicants_owner_success() {
            given(ownerDetails.getId()).willReturn(1L);
            given(author.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(applicantRepository.findByPost_IdAndStatus(1L, ApplicantStatus.PENDING))
                    .willReturn(List.of());

            List<ApplicantResponse> result = applicantService.getApplicants(1L, ownerDetails);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("방장이 아니면 예외를 던진다")
        void getApplicants_notOwner_throwsException() {
            given(ownerDetails.getId()).willReturn(99L);
            given(author.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> applicantService.getApplicants(1L, ownerDetails))
                    .isInstanceOf(PostException.class);
        }
    }

    @Nested
    @DisplayName("apply() - 스터디 신청")
    class Apply {

        @Test
        @DisplayName("정상적으로 스터디를 신청한다")
        void apply_success() {
            given(otherDetails.getId()).willReturn(2L);
            given(author.getId()).willReturn(1L);
            given(post.isClosed()).willReturn(false);
            given(post.isFull()).willReturn(false);
            given(post.getAuthor()).willReturn(author);
            given(post.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(2L, 10L))
                    .willReturn(false);
            given(applicantRepository.findByPost_IdAndMember_Id(1L, 2L)).willReturn(Optional.empty());

            applicantService.apply(1L, otherDetails);

            then(applicantRepository).should().save(any(Applicant.class));
            then(eventPublisher).should().publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("마감된 게시글에 신청하면 예외를 던진다")
        void apply_closed_throwsException() {
            given(post.isClosed()).willReturn(true);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> applicantService.apply(1L, otherDetails))
                    .isInstanceOf(PostException.class);
        }

        @Test
        @DisplayName("인원이 꽉 찬 게시글에 신청하면 예외를 던진다")
        void apply_full_throwsException() {
            given(post.isClosed()).willReturn(false);
            given(post.isFull()).willReturn(true);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> applicantService.apply(1L, otherDetails))
                    .isInstanceOf(PostException.class);
        }

        @Test
        @DisplayName("본인 게시글에 신청하면 예외를 던진다")
        void apply_ownPost_throwsException() {
            given(ownerDetails.getId()).willReturn(1L);
            given(author.getId()).willReturn(1L);
            given(post.isClosed()).willReturn(false);
            given(post.isFull()).willReturn(false);
            given(post.getAuthor()).willReturn(author);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> applicantService.apply(1L, ownerDetails))
                    .isInstanceOf(PostException.class);
        }

        @Test
        @DisplayName("이미 채팅방에 참가 중이면 예외를 던진다")
        void apply_alreadyParticipant_throwsException() {
            given(otherDetails.getId()).willReturn(2L);
            given(author.getId()).willReturn(1L);
            given(post.isClosed()).willReturn(false);
            given(post.isFull()).willReturn(false);
            given(post.getAuthor()).willReturn(author);
            given(post.getChatRoom()).willReturn(chatRoom);
            given(chatRoom.getId()).willReturn(10L);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(2L, 10L))
                    .willReturn(true);

            assertThatThrownBy(() -> applicantService.apply(1L, otherDetails))
                    .isInstanceOf(ChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("approve() - 신청 승인")
    class Approve {

        @Test
        @DisplayName("신청을 승인하면 참가자가 추가되고 이벤트가 2회 발행된다")
        void approve_success() {
            given(ownerDetails.getId()).willReturn(1L);
            given(author.getId()).willReturn(1L);
            given(applicantMember.getId()).willReturn(2L);
            given(applicantMember.getUsername()).willReturn("applicantUser");
            given(applicantMember.getNickname()).willReturn("applicantNick");

            Applicant applicant = mock(Applicant.class);
            given(applicant.getMember()).willReturn(applicantMember);
            given(post.getAuthor()).willReturn(author);
            given(post.getId()).willReturn(1L);
            given(post.getTitle()).willReturn("테스트 게시글");
            given(post.isFull()).willReturn(false);
            given(post.getChatRoom()).willReturn(chatRoom);
            given(post.getChatRoomId()).willReturn(10L);
            given(chatRoom.getId()).willReturn(10L);

            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(applicantRepository.findById(10L)).willReturn(Optional.of(applicant));

            ChatParticipant chatParticipant = mock(ChatParticipant.class);
            given(chatParticipantRepository.save(any(ChatParticipant.class))).willReturn(chatParticipant);
            given(chatRoomSequenceService.getLatestSequence(10L)).willReturn(5L);
            given(chatRoomCacheService.handleMessageDelivery(10L)).willReturn(6L);

            ChatMessage joinMessage = mock(ChatMessage.class);
            given(joinMessage.getId()).willReturn(100L);
            given(joinMessage.getSendAt()).willReturn(java.time.LocalDateTime.now());
            given(chatMessageService.saveJoinEvent(chatRoom, applicantMember, 6L)).willReturn(joinMessage);

            applicantService.approve(1L, 10L, ownerDetails);

            then(applicant).should().approve();
            then(post).should().incrementCurrentCount();
            then(chatParticipantRepository).should().save(any(ChatParticipant.class));
            then(eventPublisher).should(times(2)).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("승인으로 인원이 꽉 차면 게시글이 마감된다")
        void approve_becomeFull_closesPost() {
            given(ownerDetails.getId()).willReturn(1L);
            given(author.getId()).willReturn(1L);
            given(applicantMember.getId()).willReturn(2L);
            given(applicantMember.getUsername()).willReturn("applicantUser");
            given(applicantMember.getNickname()).willReturn("applicantNick");

            Applicant applicant = mock(Applicant.class);
            given(applicant.getMember()).willReturn(applicantMember);
            given(post.getAuthor()).willReturn(author);
            given(post.getId()).willReturn(1L);
            given(post.getTitle()).willReturn("테스트 게시글");
            given(post.getChatRoom()).willReturn(chatRoom);
            given(post.getChatRoomId()).willReturn(10L);
            given(chatRoom.getId()).willReturn(10L);
            given(post.isFull()).willReturn(true);

            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(applicantRepository.findById(10L)).willReturn(Optional.of(applicant));

            ChatParticipant chatParticipant = mock(ChatParticipant.class);
            given(chatParticipantRepository.save(any())).willReturn(chatParticipant);
            given(chatRoomSequenceService.getLatestSequence(10L)).willReturn(3L);
            given(chatRoomCacheService.handleMessageDelivery(10L)).willReturn(4L);

            ChatMessage joinMessage = mock(ChatMessage.class);
            given(joinMessage.getId()).willReturn(200L);
            given(joinMessage.getSendAt()).willReturn(java.time.LocalDateTime.now());
            given(chatMessageService.saveJoinEvent(chatRoom, applicantMember, 4L)).willReturn(joinMessage);

            applicantService.approve(1L, 10L, ownerDetails);

            then(post).should().close();
        }

        @Test
        @DisplayName("방장이 아니면 예외를 던진다")
        void approve_notOwner_throwsException() {
            given(ownerDetails.getId()).willReturn(99L);
            given(author.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> applicantService.approve(1L, 10L, ownerDetails))
                    .isInstanceOf(PostException.class);
        }

        @Test
        @DisplayName("신청자가 존재하지 않으면 예외를 던진다")
        void approve_applicantNotFound_throwsException() {
            given(ownerDetails.getId()).willReturn(1L);
            given(author.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(applicantRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> applicantService.approve(1L, 999L, ownerDetails))
                    .isInstanceOf(PostException.class);
        }
    }

    @Nested
    @DisplayName("reject() - 신청 거절")
    class Reject {

        @Test
        @DisplayName("신청을 거절하면 reject()가 호출되고 이벤트가 1회 발행된다")
        void reject_success() {
            given(ownerDetails.getId()).willReturn(1L);
            given(author.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);

            Applicant applicant = mock(Applicant.class);
            given(applicant.getMember()).willReturn(applicantMember);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(applicantRepository.findById(10L)).willReturn(Optional.of(applicant));

            applicantService.reject(1L, 10L, ownerDetails);

            then(applicant).should().reject();
            then(eventPublisher).should(times(1)).publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("방장이 아니면 예외를 던진다")
        void reject_notOwner_throwsException() {
            given(ownerDetails.getId()).willReturn(99L);
            given(author.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> applicantService.reject(1L, 10L, ownerDetails))
                    .isInstanceOf(PostException.class);
        }
    }
}