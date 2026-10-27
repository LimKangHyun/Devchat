package project.api.domain.deadletter.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import project.api.domain.deadletter.entity.StreamDeadLetter;

public interface StreamDeadLetterRepository extends JpaRepository<StreamDeadLetter, Long> {

    boolean existsByStreamKeyAndRecordId(String streamKey, String recordId);
}
