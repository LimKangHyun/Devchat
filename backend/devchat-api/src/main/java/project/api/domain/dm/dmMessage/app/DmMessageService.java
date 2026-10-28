package project.api.domain.dm.dmMessage.app;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.auth.dto.MemberDetails;
import project.api.domain.member.app.MemberService;
import project.api.domain.dm.dmRoom.app.DmRoomService;
import project.api.domain.dm.dmMessage.dao.DmMessageRepository;
import project.api.domain.dm.dmMessage.dto.DmMessageRequest;
import project.api.domain.dm.dmMessage.dto.DmMessageResponse;
import project.api.domain.dm.dmMessage.entity.DmMessage;
import project.api.domain.dm.dmRoom.entity.DmRoom;
import project.api.domain.member.entity.Member;
import project.api.domain.notification.UserNotificationSender;
import project.api.domain.notification.dto.NotificationDto;
import project.api.global.exception.errorcode.DmErrorCode;
import project.api.global.exception.ex.DmException;

@Service
@RequiredArgsConstructor
public class DmMessageService {

    private final DmRoomService dmRoomService;
    private final DmMessageRepository dmMessageRepository;
    private final MemberService memberService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserNotificationSender notificationSender;

    @Transactional
    public DmMessageResponse save(Long roomId, DmMessageRequest request, Authentication auth) {
        MemberDetails memberDetails = memberService.checkAuthentication(auth);
        Member sender = memberService.getMemberById(memberDetails.getId());

        DmRoom room = dmRoomService.getDmRoomById(roomId);

        // 대화 당사자만 보낼 수 있고, 알림은 요청 값이 아니라 방의 상대방에게 보낸다
        Member receiver = otherMemberOf(room, sender.getId());

        DmMessage dmMessage = new DmMessage(room, sender, request.content(), request.type());

        NotificationDto notificationDto = NotificationDto.ofDmMessage(dmMessage, receiver.getUsername());

        DmMessageResponse response = DmMessageResponse.from(dmMessageRepository.save(dmMessage));
        messagingTemplate.convertAndSend("/topic/dm/" + roomId, response);

        notificationSender.send(notificationDto.receiverUsername(), notificationDto);
        return response;
    }

    private Member otherMemberOf(DmRoom room, Long senderId) {
        if (room.getMember1().getId().equals(senderId)) return room.getMember2();
        if (room.getMember2().getId().equals(senderId)) return room.getMember1();
        throw new DmException(DmErrorCode.NOT_DM_MEMBER);
    }

    @Transactional
    public DmMessageResponse saveEvent(DmMessageResponse dmEvent) {
        DmRoom room = dmRoomService.getDmRoomById(dmEvent.roomId());
        Member member = memberService.getMemberById(dmEvent.senderId());

        DmMessage dmMessage = new DmMessage(room, member, dmEvent.content(), dmEvent.type());

        DmMessage save = dmMessageRepository.save(dmMessage);
        return DmMessageResponse.from(save);
    }

    @Transactional(readOnly = true)
    public Page<DmMessageResponse> getDmMessages(Long roomId, Pageable pageable,
        Authentication auth) {

        memberService.checkAuthentication(auth);

        return dmMessageRepository.findMessagesByRoomId(roomId, pageable);
    }
}
