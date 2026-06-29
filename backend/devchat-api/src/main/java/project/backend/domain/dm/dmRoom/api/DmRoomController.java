package project.backend.domain.dm.dmRoom.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.backend.domain.dm.dmRoom.app.DmRoomService;

@Tag(name = "DM Room", description = "1대1 채팅방 API")
@RestController
@RequestMapping("/dm/room")
@RequiredArgsConstructor
public class DmRoomController {

	private final DmRoomService dmRoomService;

	@Operation(summary = "DM 방 조회")
	@GetMapping("/{username}")
	public Map<String, Long> getDmRoomId(@PathVariable String username,
		Authentication authentication) {

		Long roomId = dmRoomService.getDmRoomId(username, authentication);
		return Map.of("roomId", roomId);
	}
}
