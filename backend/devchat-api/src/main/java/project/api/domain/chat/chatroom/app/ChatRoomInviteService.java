package project.api.domain.chat.chatroom.app;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.chat.chatroom.dao.ChatRoomRepository;
import project.api.domain.chat.chatroom.dto.EmailInviteResponse;
import project.api.domain.chat.chatroom.entity.ChatRoom;
import project.api.domain.member.app.MemberService;
import project.api.global.exception.errorcode.ChatRoomErrorCode;
import project.api.global.exception.ex.ChatRoomException;

/** 채팅방 이메일 초대. 참여 중인 사람만 보낼 수 있고, 사람마다 하루 발송 수를 제한한다. */
@Service
@RequiredArgsConstructor
public class ChatRoomInviteService {

    /** 한 사람이 하루에 보낼 수 있는 초대 메일 수. Gmail 발송 한도(하루 약 500통)와 스팸 악용을 고려. */
    static final int DAILY_LIMIT = 30;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantService chatRoomParticipantService;
    private final MemberService memberService;
    private final InviteMailSender inviteMailSender;
    private final StringRedisTemplate redisTemplate;

    @Transactional(readOnly = true)
    public EmailInviteResponse inviteByEmail(Long roomId, Long memberId, List<String> rawEmails) {
        chatRoomParticipantService.validateParticipant(memberId, roomId);
        ChatRoom room = chatRoomRepository.findById(roomId)
            .orElseThrow(() -> new ChatRoomException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

        List<String> emails = rawEmails.stream()
            .map(e -> e.trim().toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
        reserveDailyQuota(memberId, emails.size());

        String inviter = memberService.getMemberById(memberId).getNickname();
        inviteMailSender.send(new InviteMailSender.InviteMail(room.getName(), room.getInviteCode(), inviter, emails));
        return new EmailInviteResponse(emails.size());
    }

    /** 오늘 보낸 수에 이번 요청분을 더해 한도를 넘으면 되돌리고 거절한다. */
    private void reserveDailyQuota(Long memberId, int count) {
        String key = "invite:email:" + memberId + ":" + LocalDate.now();
        Long total = redisTemplate.opsForValue().increment(key, count);
        if (total != null && total == count) {
            redisTemplate.expire(key, Duration.ofDays(1));
        }
        if (total != null && total > DAILY_LIMIT) {
            redisTemplate.opsForValue().decrement(key, count);
            throw new ChatRoomException(ChatRoomErrorCode.INVITE_LIMIT_EXCEEDED);
        }
    }
}
