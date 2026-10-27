package project.api.domain.deadletter.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.api.domain.deadletter.app.StreamDeadLetterService;

/** AI 서버가 최대 재시도를 넘긴 메시지를 보고한다. 내부 호출 전용(/internal/**는 내부 JWT로 인증). */
@RestController
@RequestMapping("/internal/dead-letters")
@RequiredArgsConstructor
public class DeadLetterInternalController {

    private final StreamDeadLetterService streamDeadLetterService;

    @PostMapping
    public ResponseEntity<Void> save(@RequestBody DeadLetterRequest req) {
        streamDeadLetterService.save(req.streamKey(), req.recordId(), req.payload());
        return ResponseEntity.ok().build();
    }

    public record DeadLetterRequest(String streamKey, String recordId, String payload) {}
}
