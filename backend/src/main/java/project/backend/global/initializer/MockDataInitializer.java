package project.backend.global.initializer;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import project.backend.domain.chat.chatroom.dao.ChatRoomRepository;
import project.backend.domain.chat.chatroom.entity.ChatParticipant;
import project.backend.domain.chat.chatroom.entity.ChatRoom;
import project.backend.domain.imagefile.ImageFileRepository;
import project.backend.domain.member.app.MemberService;
import project.backend.domain.member.dao.MemberRepository;
import project.backend.domain.member.dto.SignUpRequest;
import project.backend.domain.member.entity.Member;

@Component
@Profile("local")
@RequiredArgsConstructor
public class MockDataInitializer {

	private final ImageFileRepository imageFileRepository;
	private final MemberService memberService;
	private final ChatRoomRepository chatRoomRepository;
	private final MemberRepository memberRepository;

	@PostConstruct
	public void generateMockUsersForSearchTest() {
		int totalMockUsers = 100; // 원하는 수만큼 생성
		for (int i = 1; i <= totalMockUsers; i++) {
			String username = "mockuser" + i;
			String email = username + "@example.com";
			String nickname = "Mock" + i;
			String password = "password";

			// 중복 방지
			if (memberRepository.findByUsername(username).isEmpty()) {
				SignUpRequest request = new SignUpRequest();
				request.setUsername(username);
				request.setEmail(email);
				request.setNickname(nickname);
				request.setPassword(password);

				memberService.saveMember(request);
			}
		}
	}
}

