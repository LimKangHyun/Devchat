package project.api.domain.member.friend.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import project.api.domain.member.entity.Member;
import java.util.List;
import project.api.domain.member.friend.dto.FriendRow;
import project.api.domain.member.friend.entity.Friends;

public interface FriendsListRepository extends JpaRepository<Friends, Long> {

	boolean existsByOwnerAndFriend(Member owner, Member friend);

	@Query("""
		    SELECT new project.api.domain.member.friend.dto.FriendRow(
		        f.friend.id,
		        f.friend.username,
		        f.friend.nickname,
		        f.friend.profileImage
		    )
		    FROM Friends f
		    WHERE f.owner.id = :ownerId
		""")
	Page<FriendRow> getFriendRows(@Param("ownerId") Long ownerId, Pageable pageable);

	/** memberId를 친구로 둔 사람들(접속 상태를 알려줄 대상) */
	@Query("SELECT f.owner.username FROM Friends f WHERE f.friend.id = :memberId")
	List<String> findOwnerUsernamesByFriendId(@Param("memberId") Long memberId);
}
