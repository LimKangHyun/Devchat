package project.backend.global.initializer;

import jakarta.annotation.PostConstruct;
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

import java.util.List;

@Component
@Profile("local")
@RequiredArgsConstructor
public class MockDataInitializer {

	private final ImageFileRepository imageFileRepository;
	private final MemberService memberService;
	private final ChatRoomRepository chatRoomRepository;
	private final MemberRepository memberRepository;


	@PostConstruct
	public void MockDataGenerator() {

		// 1. 테스트용 멤버 생성
		List<String> emails = List.of(
			"test1@test.com", "test2@test.com", "test3@test.com", "test4@test.com",
			"test5@test.com", "test6@test.com", "test7@test.com"
		);

		for (int i = 0; i < emails.size(); i++) {
			String email = emails.get(i);
			String nickname = "test" + (i + 1);
			String password = nickname;

			// 중복 방지: 이미 존재하면 skip
			if (memberRepository.findByEmail(email).isEmpty()) {
				SignUpRequest request = new SignUpRequest();
				request.setEmail(email);
				request.setNickname(nickname);
				request.setPassword(password);

				memberService.saveMember(request);
			}
		}

		List<Member> allMembers = memberRepository.findAll(); // 저장된 전체 멤버

		// 2. 각 멤버가 3개씩 채팅방 생성 + 참가자 3명씩 포함
		for (int i = 0; i < allMembers.size(); i++) {
			Member owner = allMembers.get(i);

			for (int j = 1; j < allMembers.size(); j++) {
				ChatRoom room = ChatRoom.builder()
					.name("TestRoom-" + (i + 1) + "-" + j)
					.repositoryUrl("https://github.com/test" + (i + 1) + "/repo" + j)
					.owner(owner)
					.inviteCode(UUID.randomUUID().toString())
					.build();

				// 참가자 최대 3명 포함 (owner 포함 가능)
				for (int k = 0; k < Math.min(3, allMembers.size()); k++) {
					Member participant = allMembers.get(k);

					ChatParticipant chatParticipant = ChatParticipant.builder()
						.participant(participant)
						.chatRoom(room)
						.build();

					room.getParticipants().add(chatParticipant); // 양방향 연결
				}

				chatRoomRepository.save(room); // cascade 로 참가자도 함께 저장됨
			}
		}

		chatRoomRepository.flush();
	}
}

