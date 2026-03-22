package project.backend.domain.community.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
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
import org.springframework.data.domain.SliceImpl;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.app.ChatRoomRedisService;
import project.backend.domain.chat.chatroom.app.ChatRoomService;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.community.dao.ApplicantRepository;
import project.backend.domain.community.dao.PostRepository;
import project.backend.domain.community.dto.ApplicantResponse;
import project.backend.domain.community.dto.PostCreateRequest;
import project.backend.domain.community.dto.PostResponse;
import project.backend.domain.community.dto.PostUpdateRequest;
import project.backend.domain.community.entity.Applicant;
import project.backend.domain.community.entity.ApplicantStatus;
import project.backend.domain.community.entity.Post;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.ex.ChatRoomException;
import project.backend.global.exception.ex.PostException;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @InjectMocks
    private PostService postService;

    @Mock
    private PostRepository postRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatParticipantRepository chatParticipantRepository;
    @Mock
    private ApplicantRepository applicantRepository;
    @Mock
    private ChatMessageMapper chatMessageMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ChatRoomRedisService chatRoomRedisService;
    @Mock
    private ChatRoomService chatRoomService;
    
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
    @DisplayName("getPosts() - 게시글 목록 조회")
    class GetPosts {

        @Test
        @DisplayName("myApplied=true인데 비로그인이면 예외를 던진다")
        void getPosts_myApplied_notLoggedIn_throwsException() {
            assertThatThrownBy(() ->
                postService.getPosts("recent", false, true, 0, 10, null))
                .isInstanceOf(PostException.class);
        }

        @Test
        @DisplayName("myApplied=true이면 내가 신청한 게시글 목록을 반환한다")
        void getPosts_myApplied_loggedIn_returnsAppliedPosts() {
            given(ownerDetails.getId()).willReturn(1L);
            Slice<Post> slice = new SliceImpl<>(List.of());
            given(postRepository.findAppliedByMember(eq(1L), any(PageRequest.class))).willReturn(
                slice);

            Slice<PostResponse> result = postService.getPosts("recent", false, true, 0, 10,
                ownerDetails);

            assertThat(result).isNotNull();
            then(postRepository).should().findAppliedByMember(eq(1L), any());
        }

        @Test
        @DisplayName("activeOnly=true, sort=hot이면 조회수 기준 활성 게시글을 반환한다")
        void getPosts_activeOnly_hot_returnsActiveByViewCount() {
            Slice<Post> slice = new SliceImpl<>(List.of());
            given(postRepository.findActiveByOrderByViewCountDesc(any(), any())).willReturn(slice);

            postService.getPosts("hot", true, false, 0, 10, null);

            then(postRepository).should().findActiveByOrderByViewCountDesc(any(), any());
        }

        @Test
        @DisplayName("activeOnly=true, sort=recent이면 최신순 활성 게시글을 반환한다")
        void getPosts_activeOnly_recent_returnsActiveByCreatedAt() {
            Slice<Post> slice = new SliceImpl<>(List.of());
            given(postRepository.findActiveByOrderByCreatedAtDesc(any(), any())).willReturn(slice);

            postService.getPosts("recent", true, false, 0, 10, null);

            then(postRepository).should().findActiveByOrderByCreatedAtDesc(any(), any());
        }

        @Test
        @DisplayName("activeOnly=false, sort=hot이면 전체 조회수 기준 게시글을 반환한다")
        void getPosts_all_hot_returnsAllByViewCount() {
            Slice<Post> slice = new SliceImpl<>(List.of());
            given(postRepository.findAllByOrderByViewCountDesc(any())).willReturn(slice);

            postService.getPosts("hot", false, false, 0, 10, null);

            then(postRepository).should().findAllByOrderByViewCountDesc(any());
        }

        @Test
        @DisplayName("activeOnly=false, sort=recent이면 전체 최신순 게시글을 반환한다")
        void getPosts_all_recent_returnsAllByCreatedAt() {
            Slice<Post> slice = new SliceImpl<>(List.of());
            given(postRepository.findAllByOrderByCreatedAtDesc(any())).willReturn(slice);

            postService.getPosts("recent", false, false, 0, 10, null);

            then(postRepository).should().findAllByOrderByCreatedAtDesc(any());
        }
    }

    @Nested
    @DisplayName("getPost() - 게시글 단건 조회")
    class GetPost {

        @Test
        @DisplayName("존재하는 게시글 조회 시 조회수가 증가한다")
        void getPost_exists_incrementsViewCount() {
            // NullPointer 수정: PostResponse.from()이 post.getAuthor()를 실제 호출하므로 stubbing 필요
            given(post.getAuthor()).willReturn(author);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            postService.getPost(1L);

            then(post).should().incrementViewCount();
        }

        @Test
        @DisplayName("존재하지 않는 게시글 조회 시 예외를 던진다")
        void getPost_notFound_throwsException() {
            given(postRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postService.getPost(999L))
                .isInstanceOf(PostException.class);
        }
    }

    @Nested
    @DisplayName("createPost() - 게시글 생성")
    class CreatePost {

        @Test
        @DisplayName("정상적으로 게시글을 생성한다")
        void createPost_success() {
            PostCreateRequest request = mock(PostCreateRequest.class);
            given(request.getChatRoomId()).willReturn(10L);
            given(ownerDetails.getId()).willReturn(1L);

            given(postRepository.existsByChatRoom_Id(10L)).willReturn(false);
            given(chatRoomRepository.findById(10L)).willReturn(Optional.of(chatRoom));

            Post savedPost = mock(Post.class);
            given(savedPost.getAuthor()).willReturn(author);
            given(postRepository.save(any(Post.class))).willReturn(savedPost);

            postService.createPost(request, ownerDetails);

            then(postRepository).should().save(any(Post.class));
        }

        @Test
        @DisplayName("이미 게시글이 있는 채팅방이면 예외를 던진다")
        void createPost_alreadyHasPost_throwsException() {
            PostCreateRequest request = mock(PostCreateRequest.class);
            given(request.getChatRoomId()).willReturn(10L);
            given(ownerDetails.getId()).willReturn(1L);
            given(postRepository.existsByChatRoom_Id(10L)).willReturn(true);

            assertThatThrownBy(() -> postService.createPost(request, ownerDetails))
                .isInstanceOf(PostException.class);
            then(postRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("채팅방이 존재하지 않으면 예외를 던진다")
        void createPost_chatRoomNotFound_throwsException() {
            PostCreateRequest request = mock(PostCreateRequest.class);
            given(request.getChatRoomId()).willReturn(10L);
            given(ownerDetails.getId()).willReturn(1L);
            given(postRepository.existsByChatRoom_Id(10L)).willReturn(false);
            given(chatRoomRepository.findById(10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postService.createPost(request, ownerDetails))
                .isInstanceOf(ChatRoomException.class);
        }
    }

    @Nested
    @DisplayName("updatePost() - 게시글 수정")
    class UpdatePost {

        @Test
        @DisplayName("작성자가 정상적으로 게시글을 수정한다")
        void updatePost_success() {
            given(ownerDetails.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(author.getId()).willReturn(1L);
            given(post.isClosed()).willReturn(false);
            given(post.getCurrentCount()).willReturn(2);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            PostUpdateRequest request = mock(PostUpdateRequest.class);
            given(request.getMaxCount()).willReturn(5);

            postService.updatePost(1L, request, ownerDetails);

            then(post).should().update(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("작성자가 아니면 예외를 던진다")
        void updatePost_notAuthor_throwsException() {
            given(ownerDetails.getId()).willReturn(99L);
            given(post.getAuthor()).willReturn(author);
            given(author.getId()).willReturn(1L);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() ->
                postService.updatePost(1L, mock(PostUpdateRequest.class), ownerDetails))
                .isInstanceOf(PostException.class);
        }

        @Test
        @DisplayName("마감된 게시글은 수정할 수 없다")
        void updatePost_closed_throwsException() {
            given(ownerDetails.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(author.getId()).willReturn(1L);
            given(post.isClosed()).willReturn(true);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() ->
                postService.updatePost(1L, mock(PostUpdateRequest.class), ownerDetails))
                .isInstanceOf(PostException.class);
        }

        @Test
        @DisplayName("maxCount가 현재 인원보다 작으면 예외를 던진다")
        void updatePost_maxCountInvalid_throwsException() {
            given(ownerDetails.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(author.getId()).willReturn(1L);
            given(post.isClosed()).willReturn(false);
            given(post.getCurrentCount()).willReturn(5);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            PostUpdateRequest request = mock(PostUpdateRequest.class);
            given(request.getMaxCount()).willReturn(3);

            assertThatThrownBy(() -> postService.updatePost(1L, request, ownerDetails))
                .isInstanceOf(PostException.class);
        }
    }

    @Nested
    @DisplayName("deletePost() - 게시글 삭제")
    class DeletePost {

        @Test
        @DisplayName("작성자가 정상적으로 게시글을 삭제한다")
        void deletePost_success() {
            given(ownerDetails.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(author.getId()).willReturn(1L);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            postService.deletePost(1L, ownerDetails);

            then(postRepository).should().delete(post);
        }

        @Test
        @DisplayName("작성자가 아니면 예외를 던진다")
        void deletePost_notAuthor_throwsException() {
            given(ownerDetails.getId()).willReturn(99L);
            given(post.getAuthor()).willReturn(author);
            given(author.getId()).willReturn(1L);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.deletePost(1L, ownerDetails))
                .isInstanceOf(PostException.class);
            then(postRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("존재하지 않는 게시글 삭제 시 예외를 던진다")
        void deletePost_notFound_throwsException() {
            given(postRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> postService.deletePost(999L, ownerDetails))
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
            given(chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(2L,
                10L))
                .willReturn(false);
            given(applicantRepository.findByPost_IdAndMember_Id(1L, 2L)).willReturn(
                Optional.empty());

            postService.apply(1L, otherDetails);

            then(applicantRepository).should().save(any(Applicant.class));

            then(eventPublisher).should().publishEvent(any(Object.class));
        }

        @Test
        @DisplayName("마감된 게시글에 신청하면 예외를 던진다")
        void apply_closed_throwsException() {
            given(post.isClosed()).willReturn(true);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.apply(1L, otherDetails))
                .isInstanceOf(PostException.class);
        }

        @Test
        @DisplayName("인원이 꽉 찬 게시글에 신청하면 예외를 던진다")
        void apply_full_throwsException() {
            given(post.isClosed()).willReturn(false);
            given(post.isFull()).willReturn(true);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.apply(1L, otherDetails))
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

            assertThatThrownBy(() -> postService.apply(1L, ownerDetails))
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
            given(chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(2L,
                10L))
                .willReturn(true);

            assertThatThrownBy(() -> postService.apply(1L, otherDetails))
                .isInstanceOf(ChatRoomException.class);
        }
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

            List<ApplicantResponse> result = postService.getApplicants(1L, ownerDetails);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("방장이 아니면 예외를 던진다")
        void getApplicants_notOwner_throwsException() {
            given(ownerDetails.getId()).willReturn(99L);
            given(author.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.getApplicants(1L, ownerDetails))
                .isInstanceOf(PostException.class);
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

            Applicant applicant = mock(Applicant.class);
            given(applicant.getMember()).willReturn(applicantMember);

            given(post.getAuthor()).willReturn(author);
            given(post.isFull()).willReturn(false);
            given(post.getChatRoom()).willReturn(chatRoom);
            given(post.getChatRoomId()).willReturn(10L);
            given(chatRoom.getId()).willReturn(10L);

            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(applicantRepository.findById(10L)).willReturn(Optional.of(applicant));

            ChatParticipant chatParticipant = mock(ChatParticipant.class);
            given(chatParticipantRepository.save(any(ChatParticipant.class))).willReturn(
                chatParticipant);
            given(chatRoomService.getLatestSequence(10L)).willReturn(5L);
            given(chatRoomRedisService.handleMessageDelivery(10L)).willReturn(6L);

            ChatMessage joinMessage = mock(ChatMessage.class);
            given(joinMessage.getId()).willReturn(100L);
            given(joinMessage.getSendAt()).willReturn(LocalDateTime.now());
            given(chatMessageMapper.toEntityWithJoinEvent(eq(chatRoom), eq(applicantMember), any(),
                eq(6L)))
                .willReturn(joinMessage);
            given(chatMessageRepository.save(joinMessage)).willReturn(joinMessage);

            postService.approve(1L, 10L, ownerDetails);

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

            Applicant applicant = mock(Applicant.class);
            given(applicant.getMember()).willReturn(applicantMember);

            given(post.getAuthor()).willReturn(author);
            given(post.getChatRoom()).willReturn(chatRoom);
            given(post.getChatRoomId()).willReturn(10L);
            given(chatRoom.getId()).willReturn(10L);
            given(post.isFull()).willReturn(true);

            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(applicantRepository.findById(10L)).willReturn(Optional.of(applicant));

            ChatParticipant chatParticipant = mock(ChatParticipant.class);
            given(chatParticipantRepository.save(any())).willReturn(chatParticipant);
            given(chatRoomService.getLatestSequence(10L)).willReturn(3L);
            given(chatRoomRedisService.handleMessageDelivery(10L)).willReturn(4L);

            ChatMessage joinMessage = mock(ChatMessage.class);
            given(joinMessage.getId()).willReturn(200L);
            given(joinMessage.getSendAt()).willReturn(LocalDateTime.now());
            given(chatMessageMapper.toEntityWithJoinEvent(any(), any(), any(), anyLong()))
                .willReturn(joinMessage);
            given(chatMessageRepository.save(joinMessage)).willReturn(joinMessage);

            postService.approve(1L, 10L, ownerDetails);

            then(post).should().close();
        }

        @Test
        @DisplayName("방장이 아니면 예외를 던진다")
        void approve_notOwner_throwsException() {
            given(ownerDetails.getId()).willReturn(99L);
            given(author.getId()).willReturn(1L);
            given(post.getAuthor()).willReturn(author);
            given(postRepository.findById(1L)).willReturn(Optional.of(post));

            assertThatThrownBy(() -> postService.approve(1L, 10L, ownerDetails))
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

            assertThatThrownBy(() -> postService.approve(1L, 999L, ownerDetails))
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
            // post.getChatRoom(), applicant.getMember() — reject() 서비스 코드에서 호출하지 않으므로 제거

            Applicant applicant = mock(Applicant.class);
            // CommunityMapper.toApplicantResultEvent()가 applicant.getMember().getId() 호출
            given(applicant.getMember()).willReturn(applicantMember);

            given(postRepository.findById(1L)).willReturn(Optional.of(post));
            given(applicantRepository.findById(10L)).willReturn(Optional.of(applicant));

            postService.reject(1L, 10L, ownerDetails);

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

            assertThatThrownBy(() -> postService.reject(1L, 10L, ownerDetails))
                .isInstanceOf(PostException.class);
        }
    }
}