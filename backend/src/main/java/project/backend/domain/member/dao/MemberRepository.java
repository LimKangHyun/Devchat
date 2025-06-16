package project.backend.domain.member.dao;


import jakarta.annotation.Nonnull;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import project.backend.domain.member.dto.MemberSearchResponse;
import project.backend.domain.member.entity.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByEmail(String email);

	Optional<Member> findById(@Nonnull Long id);

	Optional<Member> findByUsername(String username);

	@Query("""
		SELECT new project.backend.domain.member.dto.MemberSearchResponse(
		    m.username,
		    m.nickname,
		    'online',
		    CASE WHEN f.id IS NOT NULL THEN true ELSE false END,
		    m.profileImage
		)
		FROM Member m
		LEFT JOIN FriendsList f 
		  ON f.owner.id = :ownerId AND f.friend.id = m.id
		WHERE LOWER(m.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
		  AND m.username <> :excludeUsername
		  AND m.username <> 'GitHubBot'
		""")
	Page<MemberSearchResponse> searchByUsernameExcludeSelf(
		@Param("keyword") String keyword,
		@Param("excludeUsername") String excludeUsername,
		@Param("ownerId") Long ownerId,
		Pageable pageable
	);

// 프론트에서 친구가 아닌 멤버만 보여주려면 이게 나은
//	@Query("""
//		SELECT new project.backend.domain.member.dto.MemberSearchResponse(
//			m.username,
//			m.nickname,
//			'online',
//			false,
//			m.profileImage
//		)
//		FROM Member m
//		WHERE LOWER(m.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
//		  AND m.username <> :excludeUsername
//		  AND m.username <> 'GitHubBot'
//		  AND NOT EXISTS (
//			  SELECT 1 FROM FriendsList f
//			  WHERE f.owner.id = :ownerId AND f.friend.id = m.id
//		  )
//	""")
//	Page<MemberSearchResponse> searchOnlyNonFriends(
//		@Param("keyword") String keyword,
//		@Param("excludeUsername") String excludeUsername,
//		@Param("ownerId") Long ownerId,
//		Pageable pageable
//	);
}

