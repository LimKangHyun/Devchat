package project.ai.stream.reclaim;

/**
 * 최대 전달 횟수를 넘긴 메시지를 받는다. 이 처리가 끝나면 PendingMessageReclaimer가 ACK해서 PEL에서 뺀다.
 * 예외를 던지면 ACK하지 않고 다음 주기에 다시 데드레터 처리를 시도한다.
 *
 * @param payload 메시지 내용. 스트림에서 이미 지워졌으면 빈 문자열
 */
@FunctionalInterface
public interface DeadLetterHandler {

    void handle(String streamKey, String recordId, String payload);
}
