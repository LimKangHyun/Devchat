package project.api.domain.member.friend.dao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import project.api.domain.member.entity.Member;
import project.api.domain.member.friend.entity.FriendRequest;
import project.api.domain.member.friend.entity.RequestStatus;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

	boolean existsBySenderAndReceiverAndStatus(Member sender, Member receiver,
		RequestStatus status);

	Optional<FriendRequest> getFriendRequestBySenderAndReceiver(Member sender, Member receiver);
}
