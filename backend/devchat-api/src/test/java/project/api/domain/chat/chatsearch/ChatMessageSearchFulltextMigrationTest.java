package project.api.domain.chat.chatsearch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채팅 메시지 검색은 chat_message_search.content의 ngram FULLTEXT 인덱스가 있어야 동작한다.
 * V1에서 인덱스가 빠진 DB에 V11을 적용하면 검색이 되고, 두 번 적용해도 실패하지 않는지 확인한다.
 */
@Testcontainers
@DisplayName("채팅 검색 FULLTEXT 인덱스 마이그레이션")
class ChatMessageSearchFulltextMigrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"));

    private static final String SEARCH_SQL = """
            SELECT id FROM chat_message_search
            WHERE room_id = 1 AND MATCH(content) AGAINST ('검색어' IN BOOLEAN MODE)
            ORDER BY id DESC
            """;

    @Test
    @DisplayName("V11 적용 후 한글 키워드로 메시지를 찾고, 재적용해도 안전하다")
    void migrationEnablesKoreanSearchAndIsIdempotent() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());

        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE chat_message_search (
                        id bigint NOT NULL,
                        content text NOT NULL,
                        room_id bigint NOT NULL,
                        PRIMARY KEY (id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
                    """);
            st.execute("INSERT INTO chat_message_search VALUES "
                    + "(1, '이 메시지에는 검색어가 들어 있다', 1), "
                    + "(2, '관계없는 내용', 1), "
                    + "(3, '다른 방의 검색어', 2)");

            runV11(conn);
            runV11(conn);

            assertThat(search(st)).containsExactly(1L);
        }
    }

    private void runV11(Connection conn) {
        ScriptUtils.executeSqlScript(conn,
                new ClassPathResource("db/migration/V11__restore_chat_message_search_fulltext_index.sql"));
    }

    private List<Long> search(Statement st) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(SEARCH_SQL)) {
            while (rs.next()) ids.add(rs.getLong(1));
        }
        return ids;
    }
}
