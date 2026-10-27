package project.api.global.outbox;

/** 이벤트 종류 하나를 처리한다. OutboxRelay가 종류별로 찾아 호출한다 */
public interface OutboxHandler {

    OutboxEventType type();

    /**
     * 이벤트를 처리한다. 예외를 던지면 백오프 뒤 다시 시도한다.
     * 같은 이벤트가 두 번 처리될 수 있으므로(전송 직후 서버가 죽는 경우) 두 번 처리해도 결과가 같아야 한다.
     */
    void handle(OutboxEvent event) throws Exception;

    /** 최대 시도 횟수를 넘겨 포기했을 때. 기다리는 쪽이 영원히 기다리지 않게 실패로 끝낸다 */
    void onGiveUp(OutboxEvent event, String error);
}
