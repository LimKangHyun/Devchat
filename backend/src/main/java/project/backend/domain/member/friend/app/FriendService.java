package project.backend.domain.member.friend.app;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.backend.auth.dto.MemberDetails;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.friend.dao.FriendRequestRepository;
import project.backend.domain.member.friend.entity.FriendRequest;
import project.backend.domain.member.friend.dto.event.FriendRequestEvent;
import project.backend.domain.member.notification.dao.NotificationRepository;
import project.backend.domain.member.notification.dto.FriendRequestDto;
import project.backend.domain.member.notification.entity.Notification;
import project.backend.global.exception.errorcode.AuthErrorCode;
import project.backend.global.exception.errorcode.FriendErrorCode;
import project.backend.global.exception.ex.AuthException;
import project.backend.global.exception.ex.FriendException;

@Service
@RequiredArgsConstructor
public class FriendService {

	private final MemberService memberService;
	private final FriendRequestRepository friendRequestRepository;
	private final NotificationRepository notificationRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public void requestFriend(Authentication auth, FriendRequestDto request) {
		var memberDetails = (MemberDetails) auth.getPrincipal();

		if (memberDetails == null) {
			throw new AuthException(AuthErrorCode.UNAUTHORIZED_USER);
		}

		Member sender = MemberDetails.of(memberDetails);
		Member receiver = memberService.getMemberByUsername(request.targetUsername());

		boolean already = friendRequestRepository.existsBySenderAndReceiver(sender,
			receiver);
		if (already) {
			throw new FriendException(FriendErrorCode.ALREADY_REQUESTED_FRIEND);
		}

		FriendRequest friendRequest = FriendRequest.builder()
			.sender(sender)
			.receiver(receiver)
			.build();

		friendRequestRepository.save(friendRequest);
		notificationRepository.save(Notification.of(friendRequest));
		eventPublisher.publishEvent(FriendRequestEvent.from(sender, receiver));
	}
}
