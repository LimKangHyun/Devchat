package project.api.domain.dm.dmRoom.app;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.auth.dto.MemberDetails;
import project.api.domain.member.app.MemberService;
import project.api.domain.dm.dmRoom.dao.DmRoomRepository;
import project.api.domain.dm.dmRoom.entity.DmRoom;
import project.api.domain.member.entity.Member;
import project.api.global.exception.errorcode.DmErrorCode;
import project.api.global.exception.ex.DmException;

@Service
@Transactional
@RequiredArgsConstructor
public class DmRoomService {

	private final DmRoomRepository dmRoomRepository;
	private final MemberService memberService;

	public void creatDmRoom(Member member1, Member member2) {
		DmRoom dmRoom = new DmRoom(member1, member2);
		dmRoomRepository.save(dmRoom);
	}

	public DmRoom getDmRoomById(Long roomId) {
		return dmRoomRepository.findById(roomId)
			.orElseThrow(() -> new DmException(DmErrorCode.NOT_FOUND_DM_CHAT));
	}

	public Long getDmRoomId(String yourUsername, Authentication authentication) {
		MemberDetails memberDetails = memberService.checkAuthentication(authentication);

		Member you = memberService.getMemberByUsername(yourUsername);
		Member me = Member.of(memberDetails);

		Long minId = Math.min(me.getId(), you.getId());
		Long maxId = Math.max(me.getId(), you.getId());

		DmRoom dmRoom = dmRoomRepository.findByMembers(minId, maxId)
			.orElseThrow(() -> new DmException(DmErrorCode.NOT_FOUND_DM_CHAT));

		return dmRoom.getId();
	}
}
