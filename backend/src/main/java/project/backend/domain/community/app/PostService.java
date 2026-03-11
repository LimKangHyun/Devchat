package project.backend.domain.community.app;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.dao.ChatMessageRepository;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatmessage.mapper.ChatMessageMapper;
import project.backend.domain.chat.chatroom.app.ChatRoomService;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.dto.event.JoinChatRoomEvent;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.community.dao.ApplicantRepository;
import project.backend.domain.community.dao.PostRepository;
import project.backend.domain.community.dto.ApplicantResponse;
import project.backend.domain.community.dto.PostCreateRequest;
import project.backend.domain.community.dto.PostResponse;
import project.backend.domain.community.dto.PostUpdateRequest;
import project.backend.domain.community.entity.ApplicantStatus;
import project.backend.domain.community.entity.Post;
import project.backend.domain.community.entity.Applicant;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.PostErrorCode;
import project.backend.global.exception.ex.PostException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ApplicantRepository applicantRepository;
    private final ChatMessageMapper chatMessageMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatRoomService chatRoomService;

    // 게시글 목록 조회
    public Slice<PostResponse> getPosts(String sort, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Slice<Post> posts = sort.equals("hot")
            ? postRepository.findAllByOrderByViewCountDesc(pageable)
            : postRepository.findAllByOrderByCreatedAtDesc(pageable);
        return posts.map(PostResponse::from);
    }

    // 게시글 상세 조회 + 조회수 증가
    @Transactional
    public PostResponse getPost(Long postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
        post.incrementViewCount();
        return PostResponse.from(post);
    }

    // 게시글 작성
    @Transactional
    public PostResponse createPost(PostCreateRequest request, MemberDetails memberDetails) {
        Member author = Member.of(memberDetails);
        ChatRoom chatRoom = chatRoomRepository.findById(request.getChatRoomId())
            .orElseThrow(
                () -> new PostException(PostErrorCode.POST_NOT_FOUND)); // ChatRoom용 에러코드로 교체

        Post post = Post.builder()
            .title(request.getTitle())
            .content(request.getContent())
            .maxCount(request.getMaxCount())
            .tag(request.getTag())
            .mode(request.getMode())
            .deadline(request.getDeadline())
            .techStacks(request.getTechStacks() != null
                ? String.join(",", request.getTechStacks())
                : null)
            .author(author)
            .chatRoom(chatRoom)
            .createdAt(LocalDateTime.now())
            .build();

        return PostResponse.from(postRepository.save(post));
    }

    // 게시글 수정
    @Transactional
    public PostResponse updatePost(Long postId, PostUpdateRequest request,
        MemberDetails memberDetails) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        if (!post.getAuthor().getId().equals(memberDetails.getId())) {
            throw new PostException(PostErrorCode.POST_FORBIDDEN);
        }
        if (post.isClosed()) {
            throw new PostException(PostErrorCode.POST_ALREADY_CLOSED);
        }
        if (request.getMaxCount() != null && request.getMaxCount() < post.getCurrentCount()) {
            throw new PostException(PostErrorCode.POST_MAX_COUNT_INVALID);
        }

        post.update(
            request.getTitle(),
            request.getContent(),
            request.getMaxCount(),
            request.getTag(),
            request.getMode(),
            request.getDeadline(),
            request.getTechStacks() != null ? String.join(",", request.getTechStacks()) : null
        );

        return PostResponse.from(post);
    }

    // 게시글 삭제
    @Transactional
    public void deletePost(Long postId, MemberDetails memberDetails) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        if (!post.getAuthor().getId().equals(memberDetails.getId())) {
            throw new PostException(PostErrorCode.POST_FORBIDDEN);
        }

        postRepository.delete(post);
    }

    // 스터디 신청
    @Transactional
    public void apply(Long postId, MemberDetails memberDetails) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        if (post.isClosed()) {
            throw new PostException(PostErrorCode.POST_ALREADY_CLOSED);
        }
        if (post.isFull()) {
            throw new PostException(PostErrorCode.POST_ALREADY_FULL);
        }

        Member member = Member.of(memberDetails);

        if (post.getAuthor().getId().equals(member.getId())) {
            throw new PostException(PostErrorCode.CANNOT_APPLY_OWN_POST);
        }
        if (applicantRepository.existsByPost_IdAndMember_Id(postId, member.getId())) {
            throw new PostException(PostErrorCode.ALREADY_APPLIED);
        }

        applicantRepository.save(Applicant.of(post, member));
    }

    // 신청자 목록 조회 (방장만)
    public List<ApplicantResponse> getApplicants(Long postId, MemberDetails memberDetails) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        if (!post.getAuthor().getId().equals(memberDetails.getId())) {
            throw new PostException(PostErrorCode.POST_FORBIDDEN);
        }

        return applicantRepository.findByPost_IdAndStatus(postId, ApplicantStatus.PENDING)
            .stream()
            .map(ApplicantResponse::from)
            .toList();
    }

    @Transactional
    public void approve(Long postId, Long applicantId, MemberDetails memberDetails) {
        Post post = getPostAndValidateOwner(postId, memberDetails);
        Applicant applicant = getApplicant(applicantId);

        applicant.approve();
        post.incrementCurrentCount();
        if (post.isFull()) {
            post.close();
        }

        ChatParticipant chatParticipant = ChatParticipant.of(applicant.getMember(),
            post.getChatRoom());
        chatParticipantRepository.save(chatParticipant);

        chatRoomService.createAlarm(applicant.getMember().getId(), post.getChatRoom().getId());

        ChatMessage message = chatMessageMapper.toEntityWithJoinEvent(post.getChatRoom(),
            applicant.getMember(),
            LocalDateTime.now());
        ChatMessage savedMessage = chatMessageRepository.save(message);

        eventPublisher.publishEvent(
            new JoinChatRoomEvent(post.getChatRoom().getId(), applicant.getMember().getId(),
                applicant.getMember().getNickname(),
                savedMessage.getId(), savedMessage.getSendAt()));
    }

    @Transactional
    public void reject(Long postId, Long applicantId, MemberDetails memberDetails) {
        getPostAndValidateOwner(postId, memberDetails);
        getApplicant(applicantId).reject();
    }

    private Post getPostAndValidateOwner(Long postId, MemberDetails memberDetails) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
        if (!post.getAuthor().getId().equals(memberDetails.getId())) {
            throw new PostException(PostErrorCode.POST_FORBIDDEN);
        }
        return post;
    }

    private Applicant getApplicant(Long applicantId) {
        return applicantRepository.findById(applicantId)
            .orElseThrow(() -> new PostException(PostErrorCode.APPLICANT_NOT_FOUND));
    }
}