package project.backend.domain.member.friend.app;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.dm.dmRoom.app.DmRoomService;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.friend.dao.FriendRequestRepository;
import project.backend.domain.member.friend.dao.FriendsListRepository;
import project.backend.domain.member.friend.dto.FriendResponse;
import project.backend.domain.member.friend.entity.FriendRequest;
import project.backend.domain.member.friend.entity.RequestStatus;
import project.backend.domain.notification.dto.NotificationDto;
import project.backend.domain.member.friend.entity.Friends;
import project.backend.domain.notification.app.NotificationService;
import project.backend.domain.member.friend.dto.FriendRequestDto;
import project.backend.domain.notification.entity.Notification;
import project.backend.domain.notification.entity.NotificationType;
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
	private final DmRoomService dmRoomService;

	private record FriendContext(Member receiver, Member requester, FriendRequest friendRequest) {

	}

	//거절된 요청 찾아서 횟수제한을 두어 일정 횟수 도달시 요청 불가?
	@Transactional
	public void requestFriend(Authentication auth, FriendRequestDto request) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);

		Member sender = Member.of(memberDetails);
		Member receiver = memberService.getMemberByUsername(request.targetUsername());

		//진행중인(pending)요청이 있는지 검사
		checkPendingRequest(sender, receiver);

		checkAlreadyFriends(receiver, sender);

		FriendRequest friendRequest = prepareFriendRequest(sender, receiver);

		Notification notification = notificationService.saveNotification(
			Notification.ofFriendRequest(friendRequest));

		eventPublisher.publishEvent(NotificationDto.ofNotification(notification));
	}

	@Transactional
	public void acceptFriendRequest(Authentication auth, Long requesterId) {
		FriendContext context = prepareFriendDecisionContext(auth, requesterId);

		context.friendRequest.accept();

		Friends receiveSide = Friends.builder().owner(context.receiver).friend(context.requester)
			.build();
		Friends sendSide = Friends.builder().owner(context.requester).friend(context.receiver)
			.build();

		friendsListRepository.saveAll(List.of(receiveSide, sendSide));

		Notification acceptNotification = notificationService.saveNotification(
			Notification.ofFriendRequestByDecision(context.friendRequest,
				NotificationType.FRIEND_ACCEPTED));

		dmRoomService.creatDmRoom(context.requester, context.receiver);

		Notification BecomeFriendNotification = notificationService.saveNotification(
			Notification.ofFriendshipEstablished(context.friendRequest));

		eventPublisher.publishEvent(
			NotificationDto.ofNotification(acceptNotification));
		eventPublisher.publishEvent(
			NotificationDto.ofNotification(BecomeFriendNotification));
	}

	@Transactional
	public void rejectFriendRequest(Authentication auth, Long requesterId) {
		FriendContext context = prepareFriendDecisionContext(auth, requesterId);

		context.friendRequest.reject();

		Notification RejectNotification = notificationService.saveNotification(
			Notification.ofFriendRequestByDecision(context.friendRequest,
				NotificationType.FRIEND_REJECTED));

		eventPublisher.publishEvent(
			NotificationDto.ofNotification(RejectNotification));
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

	@Transactional(readOnly = true)
	public Page<FriendResponse> getFriends(Authentication auth, Pageable pageable) {
		MemberDetails memberDetails = memberService.checkAuthentication(auth);
		Long id = memberDetails.getId();
		return friendsListRepository.getFriendsDto(id, pageable);
	}

	private void checkAlreadyFriends(Member owner, Member friend) {
		boolean exists = friendsListRepository.existsByOwnerAndFriend(owner, friend);
		if (exists) {
			throw new FriendException(FriendErrorCode.ALREADY_FRIEND);
		}
	}

	private FriendRequest prepareFriendRequest(Member sender, Member receiver) {
		boolean rejectedRequestExists = friendRequestRepository.existsBySenderAndReceiverAndStatus(
			sender, receiver, RequestStatus.REJECTED);

		FriendRequest request = null;

		if (rejectedRequestExists) {
			request = getFriendRequestBySenderAndReceiver(sender, receiver);
			if (request.getRejectedCount() >= 3) {
				throw new FriendException(FriendErrorCode.FRIEND_REQUEST_REJECTED_LIMIT_EXCEEDED);
			}
			request.retryRequest();
		} else {
			request = new FriendRequest(receiver, sender);
			friendRequestRepository.save(request);
		}
		return request;
	}

	private void checkPendingRequest(Member sender, Member receiver) {
		boolean pendingRequestExists = friendRequestRepository.existsBySenderAndReceiverAndStatus(
			sender,
			receiver,
			RequestStatus.PENDING);
		if (pendingRequestExists) {
			throw new FriendException(FriendErrorCode.PENDING_FRIEND_REQUEST);
		}
	}
}
