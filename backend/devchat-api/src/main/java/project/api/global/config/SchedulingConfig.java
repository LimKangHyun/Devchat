package project.api.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 작업(체크포인트 재구성, 스트림 재처리 등)을 켠다.
 * 테스트 프로파일에서는 꺼서, 통합 테스트가 백그라운드 작업과 같은 DB 행을 두고 경쟁하지 않게 한다.
 * (예전에는 체크포인트 스케줄러가 테스트 도중 같은 행을 잠가 CheckpointIntegrationTest가 가끔 락 대기 시간 초과로 실패했다)
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
