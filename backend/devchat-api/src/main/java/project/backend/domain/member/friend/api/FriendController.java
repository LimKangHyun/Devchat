package project.backend.domain.member.friend.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import project.backend.domain.member.friend.dto.FriendRequestDto;

@Tag(name = "Friend", description = "친구 관리 API")
@Slf4j
@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

	private final FriendService friendService;

	@Operation(summary = "친구 요청")
	@PostMapping("/request")
	@ResponseStatus(HttpStatus.CREATED)
	public void requestFriend(Authentication auth, @RequestBody FriendRequestDto friendRequestDto) {
		friendService.requestFriend(auth, friendRequestDto);
	}

	@Operation(summary = "친구 요청 수락")
	@PostMapping("/request/{friendId}/accept")
	@ResponseStatus(HttpStatus.OK)
	public void acceptFriend(Authentication auth, @PathVariable Long friendId) {
		friendService.acceptFriendRequest(auth, friendId);
	}

	@Operation(summary = "친구 요청 거절")
	@PostMapping("/request/{friendId}/reject")
	@ResponseStatus(HttpStatus.OK)
	public void rejectFriend(Authentication auth, @PathVariable Long friendId) {
		friendService.rejectFriendRequest(auth, friendId);
	}

	@Operation(summary = "친구 목록 조회")
	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public Page<FriendResponse> getFriends(Authentication auth, Pageable pageable) {
		return friendService.getFriends(auth, pageable);
	}
}
