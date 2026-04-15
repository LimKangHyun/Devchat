package project.backend.domain.community.app;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.chat.chatmessage.app.ChatMessageService;
import project.backend.domain.chat.chatmessage.entity.ChatMessage;
import project.backend.domain.chat.chatroom.app.ChatRoomAlarmService;
import project.backend.domain.chat.chatroom.app.ChatRoomCacheService;
import project.backend.domain.chat.chatroom.app.ChatRoomSequenceService;
import project.backend.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.community.dao.ApplicantRepository;
import project.backend.domain.community.dao.PostRepository;
import project.backend.domain.community.dto.ApplicantResponse;
import project.backend.domain.community.entity.Applicant;
import project.backend.domain.community.entity.ApplicantStatus;
import project.backend.domain.community.entity.Post;
import project.backend.domain.member.entity.Member;
import project.backend.global.exception.errorcode.ChatRoomErrorCode;
import project.backend.global.exception.errorcode.PostErrorCode;
import project.backend.global.exception.ex.ChatRoomException;
import project.backend.global.exception.ex.PostException;

import java.util.List;

import static project.backend.domain.community.mapper.CommunityMapper.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicantService {

    private final PostRepository postRepository;
    private final ApplicantRepository applicantRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final ChatMessageService chatMessageService;
    private final ChatRoomCacheService chatRoomCacheService;
    private final ChatRoomAlarmService chatRoomAlarmService;
    private final ChatRoomSequenceService chatRoomSequenceService;

    public List<ApplicantResponse> getApplicants(Long postId, MemberDetails memberDetails) {
        Post post = getPostAndValidateOwner(postId, memberDetails);

        return applicantRepository.findByPost_IdAndStatus(postId, ApplicantStatus.PENDING)
                .stream()
                .map(ApplicantResponse::from)
                .toList();
    }

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

        eventPublisher.publishEvent(toApplyEvent(post, member));
        applicantRepository.save(Applicant.of(post, member));
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

        ChatParticipant chatParticipant = ChatParticipant.of(applicant.getMember(), post.getChatRoom());
        chatParticipantRepository.save(chatParticipant);

        Long currentSequence = chatRoomSequenceService.getLatestSequence(post.getChatRoom().getId());
        chatParticipant.updateLastReadSequence(currentSequence);

        chatRoomAlarmService.createAlarm(applicant.getMember().getId(), post.getChatRoom().getId());

        Long seq = chatRoomCacheService.handleMessageDelivery(post.getChatRoomId());
        ChatMessage savedMessage = chatMessageService.saveJoinEvent(post.getChatRoom(), applicant.getMember(), seq);

        eventPublisher.publishEvent(toJoinChatRoomEvent(post, applicant.getMember(), savedMessage));
        eventPublisher.publishEvent(toApplicantResultEvent(post, applicant, true));
    }

    @Transactional
    public void reject(Long postId, Long applicantId, MemberDetails memberDetails) {
        Post post = getPostAndValidateOwner(postId, memberDetails);
        Applicant applicant = getApplicant(applicantId);
        applicant.reject();

        eventPublisher.publishEvent(toApplicantResultEvent(post, applicant, false));
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