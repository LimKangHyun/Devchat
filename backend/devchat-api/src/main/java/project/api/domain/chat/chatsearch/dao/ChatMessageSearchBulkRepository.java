package project.api.domain.chat.chatsearch.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.api.domain.chat.chatsearch.entity.ChatMessageSearch;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatMessageSearchBulkRepository {

    private final JdbcTemplate jdbcTemplate;

    public void bulkInsertIgnore(List<ChatMessageSearch> list) {
        String sql = """
                INSERT INTO chat_message_search (id, room_id, content)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE id = id
                """;

        jdbcTemplate.batchUpdate(sql, list, list.size(), (ps, search) -> {
            ps.setLong(1, search.getId());
            ps.setLong(2, search.getRoomId());
            ps.setString(3, search.getContent());
        });
    }
}