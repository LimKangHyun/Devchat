package project.backend.domain.member.friend.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.backend.domain.member.friend.app.FriendService;
import project.backend.domain.member.friend.dto.FriendResponse;
import project.backend.domain.member.notification.dto.FriendRequestDto;

@Slf4j
@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

	private final FriendService friendService;

	@PostMapping("/request")
	@ResponseStatus(HttpStatus.CREATED)
	public void requestFriend(Authentication auth, @RequestBody FriendRequestDto friendRequestDto) {
		friendService.requestFriend(auth, friendRequestDto);
	}

	@PostMapping("/request/{friendId}/accept")
	@ResponseStatus(HttpStatus.OK)
	public void acceptFriend(Authentication auth, @PathVariable Long friendId) {
		friendService.acceptFriendRequest(auth, friendId);
	}

	@PostMapping("/request/{friendId}/reject")
	@ResponseStatus(HttpStatus.OK)
	public void rejectFriend(Authentication auth, @PathVariable Long friendId) {
		friendService.rejectFriendRequest(auth, friendId);
	}

	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public Page<FriendResponse> getFriends(Authentication auth, Pageable pageable) {
		return friendService.getFriends(auth, pageable);
	}
}
