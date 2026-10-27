package project.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import project.ai.indexing.EmbeddingService;
import project.ai.indexing.RepoIndexingService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TokenMeasureController {

    private final RepoIndexingService repoIndexingService;
    private final EmbeddingService embeddingService;

    @PostMapping("/token-count")
    public Mono<Map<String, Integer>> measure(
        @RequestParam String repoUrl,
        @RequestParam Long memberId
    ) {

        return Mono.fromCallable(() -> {

            List<String> chunks =
                repoIndexingService.chunkOnlyForMeasurement("https://github.com/spring-projects/spring-petclinic.git");

            int totalTokens =
                embeddingService.countTokens(chunks);

            return Map.of(
                "chunkCount", chunks.size(),
                "totalTokens", totalTokens,
                "avgTokensPerChunk",
                chunks.isEmpty() ? 0 : totalTokens / chunks.size()
            );

        }).subscribeOn(Schedulers.boundedElastic());
    }
}