package project.api.domain.chat.chatroom.dao;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 채팅방과 딸린 데이터를 테이블마다 DELETE 한 번으로 지운다.
 *
 * Spring Data의 파생 삭제(deleteByXxx)는 행을 전부 조회한 뒤 한 건씩 DELETE해서, 메시지가 많은 방일수록 삭제가 느려진다.
 * 여기서는 외래 키 순서(자식 → 부모)대로 일괄 삭제한다.
 * 영속성 컨텍스트를 거치지 않으므로 호출 전에 flush하고, 호출 뒤에는 clear해서 지워진 엔티티가 남지 않게 한다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatRoomDataCleaner {

    private static final int IN_CHUNK = 1000;

    private final EntityManager em;

    public void deleteRoomAndData(Long roomId) {
        em.flush();

        // AI 리뷰 하위 데이터 (chat_message가 ai_review를 참조하므로 ai_review 자체는 메시지 뒤에 지운다)
        int mods = exec("""
            DELETE m FROM ai_comment_mod m
            JOIN ai_review_comment c ON m.comment_id = c.id
            JOIN ai_review r ON c.ai_review_id = r.id
            WHERE r.room_id = :roomId""", roomId);
        int comments = exec("""
            DELETE c FROM ai_review_comment c
            JOIN ai_review r ON c.ai_review_id = r.id
            WHERE r.room_id = :roomId""", roomId);
        exec("""
            DELETE f FROM ai_review_file f
            JOIN ai_review r ON f.ai_review_id = r.id
            WHERE r.room_id = :roomId""", roomId);

        // 메시지 (code_review는 ON DELETE CASCADE)
        exec("DELETE FROM chat_message_index_status WHERE room_id = :roomId", roomId);
        exec("DELETE FROM chat_message_search WHERE room_id = :roomId", roomId);
        @SuppressWarnings("unchecked")
        List<Number> imageIds = em.createNativeQuery(
                "SELECT chat_image_id FROM chat_message WHERE room_id = :roomId AND chat_image_id IS NOT NULL")
            .setParameter("roomId", roomId)
            .getResultList();
        int messages = exec("DELETE FROM chat_message WHERE room_id = :roomId", roomId);
        for (int i = 0; i < imageIds.size(); i += IN_CHUNK) {
            em.createNativeQuery("DELETE FROM image_file WHERE image_id IN (:ids)")
                .setParameter("ids", imageIds.subList(i, Math.min(i + IN_CHUNK, imageIds.size())))
                .executeUpdate();
        }

        exec("DELETE FROM ai_review WHERE room_id = :roomId", roomId);
        exec("DELETE FROM chat_participant WHERE room_id = :roomId", roomId);
        exec("DELETE FROM chat_room_alarm WHERE room_id = :roomId", roomId);
        exec("DELETE FROM chat_room_checkpoint WHERE room_id = :roomId", roomId);
        exec("DELETE FROM chat_room WHERE room_id = :roomId", roomId);

        em.clear();
        log.info("채팅방 삭제. roomId={}, messages={}, images={}, aiComments={}, aiCommentMods={}",
            roomId, messages, imageIds.size(), comments, mods);
    }

    private int exec(String sql, Long roomId) {
        return em.createNativeQuery(sql).setParameter("roomId", roomId).executeUpdate();
    }
}
