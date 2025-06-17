package project.backend.domain.member.friend.app;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.friend.dao.FriendRequestRepository;
import project.backend.domain.member.friend.dao.FriendsListRepository;
import project.backend.domain.member.friend.dto.FriendResponse;
import project.backend.domain.member.friend.entity.FriendRequest;
import project.backend.domain.member.notification.dto.NotificationResponse;
import project.backend.domain.member.friend.entity.Friends;
import project.backend.domain.member.notification.app.NotificationService;
import project.backend.domain.member.friend.dto.FriendRequestDto;
import project.backend.domain.member.notification.entity.Notification;
import project.backend.domain.member.notification.entity.NotificationType;
import project.backend.global.exception.errorcode.FriendErrorCode;
import project.backend.global.exception.ex.FriendException;

@Service
@RequiredArgsConstructor
public class FriendService {

	private final MemberService memberService;
	private final FriendRequestRepository friendRequestRepository;
	private final FriendsListRepository friendsListRepository;
	private final NotificationService notificationService;
	private final ApplicationEventPublisher eventPublisher;

	private record FriendContext(Member receiver, Member requester, FriendRequest friendRequest) {

	}

	@Transactional
	public void requestFriend(Authentication auth, FriendRequestDto request) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);

		Member sender = Member.of(memberDetails);
		Member receiver = memberService.getMemberByUsername(request.targetUsername());

		checkAvailableRequest(sender, receiver);
		checkAlreadyFriends(receiver, sender);

		FriendRequest friendRequest = FriendRequest.builder()
			.sender(sender)
			.receiver(receiver)
			.build();

		friendRequestRepository.save(friendRequest);
		notificationService.saveNotification(
			Notification.ofFriendRequest(friendRequest));

		eventPublisher.publishEvent(NotificationResponse.ofFriendRequest(sender, receiver));
	}

	@Transactional
	public void acceptFriendRequest(Authentication auth, Long requesterId) {
		FriendContext context = prepareFriendDecisionContext(auth, requesterId);

		context.friendRequest.accept();

		Friends receiveSide = Friends.builder().owner(context.receiver).friend(context.requester)
			.build();
		Friends sendSide = Friends.builder().owner(context.requester).friend(context.receiver)
			.build();

		friendsListRepository.save(receiveSide);
		friendsListRepository.save(sendSide);

		notificationService.saveNotification(
			Notification.ofFriendRequestByDecision(context.friendRequest,
				NotificationType.FRIEND_ACCEPTED));
		notificationService.saveNotification(
			Notification.ofFriendshipEstablished(context.friendRequest));

		eventPublisher.publishEvent(
			NotificationResponse.ofFriendAcceptEvent(context.receiver, context.requester));
		eventPublisher.publishEvent(
			NotificationResponse.ofFriendAcceptSelf(context.receiver, context.requester));
	}

	@Transactional
	public void rejectFriendRequest(Authentication auth, Long requesterId) {
		FriendContext context = prepareFriendDecisionContext(auth, requesterId);

		context.friendRequest.reject();

		notificationService.saveNotification(
			Notification.ofFriendRequestByDecision(context.friendRequest,
				NotificationType.FRIEND_REJECTED));
		
		eventPublisher.publishEvent(
			NotificationResponse.ofFriendRejectEvent(context.receiver, context.requester));
	}

	private FriendContext prepareFriendDecisionContext(Authentication auth, Long requesterId) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);
		Member receiver = Member.of(memberDetails);
		Member requester = memberService.getMemberById(requesterId);

		Notification notification = notificationService.getNotificationByType(receiver, requester,
			NotificationType.FRIEND_REQUESTED);
		notification.markAsRead();

		checkAlreadyFriends(receiver, requester);
		FriendRequest friendRequest = getFriendRequestBySenderAndReceiver(requester, receiver);

		return new FriendContext(receiver, requester, friendRequest);
	}

	private FriendRequest getFriendRequestBySenderAndReceiver(Member sender, Member receiver) {
		return friendRequestRepository.getFriendRequestBySenderAndReceiver(sender, receiver)
			.orElseThrow(() -> new FriendException(FriendErrorCode.NOT_FOUNT_FRIEND_REQUEST));
	}

	public Page<FriendResponse> getFriends(Authentication auth, Pageable pageable) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);
		Long id = memberDetails.getId();
		return friendsListRepository.getFriendsDto(id, pageable);
	}

	private void checkAlreadyFriends(Member owner, Member friend) {
		boolean exists = friendsListRepository.existsByOwnerAndFriend(owner, friend);
		if (exists) {
			throw new FriendException(FriendErrorCode.ALREADY_REQUESTED_FRIEND);
		}
	}

	private void checkAvailableRequest(Member sender, Member receiver) {
		boolean exists = friendRequestRepository.existsBySenderAndReceiver(sender, receiver);
		if (exists) {
			throw new FriendException(FriendErrorCode.ALREADY_REQUESTED_FRIEND);
		}
	}
}
