package project.backend.domain.community.dao;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import project.backend.domain.community.entity.Post;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 최신순
    @Query("SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.createdAt DESC")
    Slice<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 인기순 (조회수)
    @Query("SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.viewCount DESC")
    Slice<Post> findAllByOrderByViewCountDesc(Pageable pageable);

    void deleteByChatRoom_Id(Long roomId);
}