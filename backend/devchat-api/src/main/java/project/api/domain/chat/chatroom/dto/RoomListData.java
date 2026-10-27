package project.api.domain.chat.chatroom.dto;

import project.api.domain.chat.chatroom.dao.ChatRoomWithSequenceProjection;

import java.util.List;
import java.util.Map;

/**
 * 채팅방 목록 조회 시 DB에서 먼저 읽어오는 데이터.
 * Redis 조회 전에 트랜잭션을 닫기 위해 분리한다.
 */
public record RoomListData(
        List<ChatRoomWithSequenceProjection> projections,
        List<Long> roomIds,
        Map<Long, Boolean> alarmEnabledMap
) {}