package project.backend.domain.member.friend.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import project.backend.domain.member.entity.Member;
import project.backend.domain.member.friend.entity.FriendRequest;
import project.backend.domain.member.friend.entity.RequestStatus;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, Long> {

	boolean existsBySenderAndReceiver(Member sender, Member receiver);
}
