package project.backend.domain.member.friend.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.friend.dto.FriendResponse;
import project.backend.domain.member.friend.entity.FriendsList;

public interface FriendsListRepository extends JpaRepository<FriendsList, Long> {

	boolean existsByOwnerAndFriend(Member owner, Member friend);

	@Query("""
		    SELECT new project.backend.domain.member.friend.dto.FriendResponse(
		        f.friend.username,
		        f.friend.nickname,
		        "online",
		        f.friend.profileImage
		    )
		    FROM FriendsList f
		    WHERE f.owner.id = :ownerId
		""")
	Page<FriendResponse> getFriends(@Param("ownerId") Long ownerId, Pageable pageable);
}
