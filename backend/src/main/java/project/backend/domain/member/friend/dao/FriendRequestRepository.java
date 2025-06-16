package project.backend.domain.member.friend.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.friend.entity.FriendRequest;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

	boolean existsBySenderAndReceiver(Member sender, Member receiver);

	List<FriendRequest> getFriendRequestsBySenderAndReceiver(Member sender, Member receiver);

	Optional<FriendRequest> getFriendRequestBySenderAndReceiver(Member sender, Member receiver);
}
