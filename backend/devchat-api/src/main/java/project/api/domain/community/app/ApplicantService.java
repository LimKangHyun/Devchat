package project.api.domain.community.app;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.auth.dto.MemberDetails;
import project.api.domain.chat.chatmessage.app.ChatMessageService;
import project.api.domain.chat.chatmessage.entity.ChatMessage;
import project.api.domain.chat.chatroom.app.ChatRoomAlarmService;
import project.api.domain.chat.chatroom.app.ChatRoomSequenceService;
import project.api.domain.chat.chatroom.app.ChatRoomReadService;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.chat.chatroom.entity.ChatParticipant;
import project.api.domain.community.dao.ApplicantRepository;
import project.api.domain.community.dao.PostRepository;
import project.api.domain.community.dto.ApplicantResponse;
import project.api.domain.community.entity.Applicant;
import project.api.domain.community.entity.ApplicantStatus;
import project.api.domain.community.entity.Post;
import project.api.domain.member.entity.Member;
import project.api.global.exception.errorcode.ChatRoomErrorCode;
import project.api.global.exception.errorcode.PostErrorCode;
import project.api.global.exception.ex.ChatRoomException;
import project.api.global.exception.ex.PostException;

import java.util.List;

import static project.api.domain.community.mapper.CommunityMapper.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicantService {

    private final PostRepository postRepository;
    private final ApplicantRepository applicantRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final ChatMessageService chatMessageService;
    private final ChatRoomSequenceService chatRoomSequenceService;
    private final ChatRoomAlarmService chatRoomAlarmService;
    private final ChatRoomReadService chatRoomReadService;

    public List<ApplicantResponse> getApplicants(Long postId, MemberDetails memberDetails) {
        getPostAndValidateOwner(postId, memberDetails);

        return applicantRepository.findByPost_IdAndStatus(postId, ApplicantStatus.PENDING)
                .stream()
                .map(ApplicantResponse::from)
                .toList();
    }

    @Transactional
    public void apply(Long postId, MemberDetails memberDetails) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND));

        validateApply(post, memberDetails);

        Member member = Member.of(memberDetails);

        applicantRepository.findByPost_IdAndMember_Id(postId, member.getId())
                .ifPresent(existing -> {
                    existing.validateReapply();
                    applicantRepository.delete(existing);
                });

        eventPublisher.publishEvent(toApplyEvent(post, member));
        applicantRepository.save(Applicant.of(post, member));
    }

    private void validateApply(Post post, MemberDetails memberDetails) {
        if (post.isClosed()) {
            throw new PostException(PostErrorCode.POST_ALREADY_CLOSED);
        }
        if (post.isFull()) {
            throw new PostException(PostErrorCode.POST_ALREADY_FULL);
        }
        if (post.getAuthor().getId().equals(memberDetails.getId())) {
            throw new PostException(PostErrorCode.CANNOT_APPLY_OWN_POST);
        }
        if (chatParticipantRepository.existsByParticipantIdAndChatRoomIdAndIsActiveTrue(
                memberDetails.getId(), post.getChatRoom().getId())) {
            throw new ChatRoomException(ChatRoomErrorCode.ALREADY_PARTICIPANT);
        }
    }

    @Transactional
    public void updateStatus(Long postId, Long applicantId, ApplicantStatus status, MemberDetails memberDetails) {

        Post post = getPostAndValidateOwner(postId, memberDetails);
        Applicant applicant = getApplicant(applicantId);

        switch (status) {
            case APPROVED -> handleApprove(post, applicant);
            case REJECTED -> handleReject(post, applicant);
        }
    }

    private ChatMessage joinChatRoom(Member member, Post post) {
        Long currentSequence = chatRoomReadService.getLatestSequence(post.getChatRoom().getId());

        ChatParticipant participant = ChatParticipant.of(member, post.getChatRoom());
        participant.updateLastReadSequence(currentSequence);
        chatParticipantRepository.save(participant);

        chatRoomAlarmService.createAlarm(member.getId(), post.getChatRoom().getId());

        chatRoomSequenceService.genMessageSeq(post.getChatRoomId());
        return chatMessageService.saveJoinEvent(post.getChatRoom(), member);
    }

    private void handleApprove(Post post, Applicant applicant) {
        applicant.approve();
        post.incrementCurrentCount();

        if (post.isFull()) {
            post.close();
        }

        ChatMessage joinMessage = joinChatRoom(applicant.getMember(), post);

        eventPublisher.publishEvent(toJoinChatRoomEvent(post, applicant.getMember(), joinMessage));
        eventPublisher.publishEvent(toApplicantResultEvent(post, applicant, true));
    }

    private void handleReject(Post post, Applicant applicant) {
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