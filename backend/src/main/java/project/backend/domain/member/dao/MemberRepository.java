package project.backend.domain.member.dao;


import jakarta.annotation.Nonnull;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import project.backend.domain.member.entity.Member;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

	@EntityGraph(attributePaths = {"profileImage"})
	Optional<Member> findByEmail(String email);

	@EntityGraph(attributePaths = {"profileImage"})
	Optional<Member> findById(@Nonnull Long id);

	@EntityGraph(attributePaths = {"profileImage"})
	Optional<Member> findByUsername(String username);
}
