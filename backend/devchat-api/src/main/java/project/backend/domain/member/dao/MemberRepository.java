package project.backend.domain.member.dao;


import jakarta.annotation.Nonnull;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
				CASE WHEN fr.status = 'ACCEPTED' THEN true ELSE false END,
				CASE WHEN fr.status = 'PENDING' THEN true ELSE false END,
				m.profileImage
			)
			FROM Member m
			LEFT JOIN FriendRequest fr ON fr.sender.id = :ownerId AND fr.receiver.id = m.id
			WHERE LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%'))
			  AND m.nickname <> :excludeNickname
			  AND m.nickname <> 'GitHubBot'
		""")
	Page<MemberSearchResponse> searchByNicknameExcludeSelf(
		@Param("keyword") String keyword,
		@Param("excludeNickname") String excludeNickname,
		@Param("ownerId") Long ownerId,
		Pageable pageable
	);

	boolean existsByUsername(String username);
}

