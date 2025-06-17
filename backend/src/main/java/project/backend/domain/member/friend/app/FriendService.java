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
import project.backend.domain.member.friend.dto.event.FriendEvent;
import project.backend.domain.member.friend.entity.FriendsList;
import project.backend.domain.member.notification.app.NotificationService;
import project.backend.domain.member.notification.dto.FriendRequestDto;
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


	@Transactional
	public void requestFriend(Authentication auth, FriendRequestDto request) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);

		Member sender = MemberDetails.of(memberDetails);
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

		eventPublisher.publishEvent(FriendEvent.ofFriendRequest(sender, receiver));
	}

	private FriendRequest getFriendRequestBySenderAndReceiver(Member sender, Member receiver) {
		return friendRequestRepository.getFriendRequestBySenderAndReceiver(sender, receiver)
			.orElseThrow(() -> new FriendException(FriendErrorCode.NOT_FOUNT_FRIEND_REQUEST));
	}

	public Page<FriendResponse> getFriends(Authentication auth, Pageable pageable) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);
		Long id = memberDetails.getId();
		return friendsListRepository.getFriends(id, pageable);
	}

	@Transactional
	public void handleFriendRequestDecision(
		Authentication auth,
		Long requesterId,
		NotificationType decisionType
	) {
		if (decisionType != NotificationType.FRIEND_ACCEPTED
			&& decisionType != NotificationType.FRIEND_REJECTED) {
			throw new IllegalArgumentException("Invalid decision type: " + decisionType);
		}

		MemberDetails memberDetails = memberService.checkAuthentication(auth);
		Member me = MemberDetails.of(memberDetails);
		Member requester = memberService.getMemberById(requesterId);

		Notification notification = notificationService.getNotificationByType(me, requester,
			NotificationType.FRIEND_REQUESTED);
		notification.markAsRead();

		checkAlreadyFriends(requester, me);

		FriendRequest friendRequest = getFriendRequestBySenderAndReceiver(requester, me);

		if (decisionType == NotificationType.FRIEND_ACCEPTED) {
			friendRequest.accept();

			// 친구 테이블에 양방향 저장
			FriendsList receiveSide = FriendsList.builder().owner(me).friend(requester).build();
			FriendsList sendSide = FriendsList.builder().owner(requester).friend(me).build();
			friendsListRepository.save(receiveSide);
			friendsListRepository.save(sendSide);
		} else {
			friendRequest.reject();
		}

		notificationService.saveNotification(
			Notification.ofFriendRequestByDecision(friendRequest, decisionType));

		notificationService.saveNotification(
			Notification.ofFriendshipEstablished(friendRequest)
		);

		// 이벤트 발행
		if (decisionType == NotificationType.FRIEND_ACCEPTED) {
			eventPublisher.publishEvent(FriendEvent.ofFriendAcceptEvent(me, requester));
			eventPublisher.publishEvent(FriendEvent.ofFriendAcceptSelf(me, requester));
		} else {
			eventPublisher.publishEvent(FriendEvent.ofFriendRejectEvent(me, requester));
		}
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
