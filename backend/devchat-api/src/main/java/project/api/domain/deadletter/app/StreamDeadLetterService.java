package project.api.domain.deadletter.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.api.domain.deadletter.dao.StreamDeadLetterRepository;
import project.api.domain.deadletter.entity.StreamDeadLetter;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamDeadLetterService {

    private final StreamDeadLetterRepository streamDeadLetterRepository;

    /** 같은 메시지를 두 번 보고해도(재시도 등) 한 번만 남긴다. */
    @Transactional
    public void save(String streamKey, String recordId, String payload) {
        if (streamDeadLetterRepository.existsByStreamKeyAndRecordId(streamKey, recordId)) {
            return;
        }
        streamDeadLetterRepository.save(new StreamDeadLetter(streamKey, recordId, payload));
        log.error("스트림 메시지 데드레터 기록 - stream={}, recordId={}", streamKey, recordId);
    }
}
