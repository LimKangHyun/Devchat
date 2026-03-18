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
import project.backend.domain.community.mapper.CommunityMapper;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.errorcode.PostErrorCode;
import project.backend.global.exception.ex.ChatRoomException;
import project.backend.global.exception.ex.PostException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static project.backend.domain.community.mapper.CommunityMapper.*;

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

    public Slice<PostResponse> getPosts(String sort, boolean activeOnly, boolean myApplied,
        int page, int size, MemberDetails memberDetails) {

        PageRequest pageable = PageRequest.of(page, size);

        if (myApplied) {
            if (memberDetails == null) {
                throw new PostException(PostErrorCode.POST_FORBIDDEN);
            }
            return postRepository
                .findAppliedByMember(memberDetails.getId(), pageable)
                .map(PostResponse::from);
        }

        Slice<Post> posts;
        if (activeOnly) {
            LocalDate today = LocalDate.now();
            posts = sort.equals("hot")
                ? postRepository.findActiveByOrderByViewCountDesc(pageable, today)
                : postRepository.findActiveByOrderByCreatedAtDesc(pageable, today);
        } else {
            posts = sort.equals("hot")
                ? postRepository.findAllByOrderByViewCountDesc(pageable)
                : postRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return posts.map(PostResponse::from);
    }

    @Transactional
    public PostResponse getPost(Long postId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));
        post.incrementViewCount();
        return PostResponse.from(post);
    }

    @Transactional
    public PostResponse createPost(PostCreateRequest request, MemberDetails memberDetails) {
        Member author = Member.of(memberDetails);

        if (postRepository.existsByChatRoom_Id(request.getChatRoomId())) {
            throw new PostException(PostErrorCode.CHATROOM_ALREADY_HAS_POST);
        }

        ChatRoom chatRoom = chatRoomRepository.findById(request.getChatRoomId())
            .orElseThrow(
                () -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

        return PostResponse.from(
            postRepository.save(CommunityMapper.toPost(request, author, chatRoom)));
    }

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

        if (chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(
                member.getId(), post.getChatRoom().getId())) {
            throw new ChatRoomException(ChatRoomErrorCode.ALREADY_PARTICIPANT);
        }

        applicantRepository.findByPost_IdAndMember_Id(postId, member.getId())
            .ifPresent(existing -> {
                existing.validateReapply();
                applicantRepository.delete(existing);
            });

        eventPublisher.publishEvent(CommunityMapper.toApplyEvent(post, member));

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

        Long currentSequence = chatRoomService.getLatestSequence(post.getChatRoom().getId());
        chatParticipant.updateLastReadSequence(currentSequence);

        chatRoomService.createAlarm(applicant.getMember().getId(), post.getChatRoom().getId());

        ChatMessage message = chatMessageMapper.toEntityWithJoinEvent(post.getChatRoom(),
            applicant.getMember(),
            LocalDateTime.now());
        ChatMessage savedMessage = chatMessageRepository.save(message);

        eventPublisher.publishEvent(toJoinChatRoomEvent(post, applicant.getMember(), savedMessage));
        eventPublisher.publishEvent(toApplicantResultEvent(post, applicant, true));
    }

    @Transactional
    public void reject(Long postId, Long applicantId, MemberDetails memberDetails) {
        Post post = getPostAndValidateOwner(postId, memberDetails);
        Applicant applicant = getApplicant(applicantId);
        applicant.reject();

        eventPublisher.publishEvent(CommunityMapper.toApplicantResultEvent(post, applicant, false));
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