package project.api.domain.push.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.push.entity.PushSubscription;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    List<PushSubscription> findByMember_Id(Long memberId);

    @Transactional
    long deleteByEndpointHash(String endpointHash);

    @Transactional
    long deleteByEndpointHashAndMember_Id(String endpointHash, Long memberId);
}
