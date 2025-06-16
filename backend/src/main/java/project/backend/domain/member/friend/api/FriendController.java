package project.backend.domain.member.friend.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.backend.domain.member.friend.app.FriendService;
import project.backend.domain.member.friend.entity.FriendRequest;

@Slf4j
@RestController
@RequestMapping("/friend")
@RequiredArgsConstructor
public class FriendController {

	private final FriendService friendService;

	@PostMapping("/request")
	@ResponseStatus(HttpStatus.CREATED)
	public void requestFriend(Authentication auth, String username) {
		friendService.requestFriend(auth, username);
	}
}
