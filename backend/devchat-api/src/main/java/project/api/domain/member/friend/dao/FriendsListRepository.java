package project.api.domain.member.friend.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.member.entity.Member;
import project.api.domain.member.friend.dto.FriendResponse;
import project.api.domain.member.friend.entity.Friends;

public interface FriendsListRepository extends JpaRepository<Friends, Long> {

	boolean existsByOwnerAndFriend(Member owner, Member friend);

	@Query("""
		    SELECT new project.api.domain.member.friend.dto.FriendResponse(
		        f.friend.username,
		        f.friend.nickname,
		        "online",
		        f.friend.profileImage
		    )
		    FROM Friends f
		    WHERE f.owner.id = :ownerId
		""")
	Page<FriendResponse> getFriendsDto(@Param("ownerId") Long ownerId, Pageable pageable);
}
