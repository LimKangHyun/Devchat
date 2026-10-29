package project.api.domain.schedule.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import project.api.domain.chat.chatroom.dao.ChatParticipantRepository;
import project.api.domain.member.dao.MemberRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.member.friend.dao.FriendsListRepository;
import project.api.global.exception.errorcode.ScheduleErrorCode;
import project.api.global.exception.ex.ScheduleException;

// 참여자로 넣을 수 있는 사람: 내 친구, 또는 나와 같은 활성 채팅방에 있는 사람
@Component
@RequiredArgsConstructor
public class ParticipantPolicy {

    private final MemberRepository memberRepository;
    private final FriendsListRepository friendsListRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    /** 검사를 통과한 참여자를 순서대로 돌려준다. 나 자신·중복·null은 조용히 뺀다 */
    public List<Member> validate(Long ownerId, List<Long> participantIds) {
        if (participantIds == null || participantIds.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long id : participantIds) {
            if (id != null && !Objects.equals(id, ownerId)) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }

        Member owner = memberRepository.getReferenceById(ownerId);
        List<Member> result = new ArrayList<>();
        for (Long id : ids) {
            Member m = memberRepository.findById(id)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.INVALID_PARTICIPANT));
            boolean allowed = friendsListRepository.existsByOwnerAndFriend(owner, m)
                || chatParticipantRepository.existsSharedActiveRoom(ownerId, id);
            if (!allowed) {
                throw new ScheduleException(ScheduleErrorCode.INVALID_PARTICIPANT);
            }
            result.add(m);
        }
        return result;
    }
}
