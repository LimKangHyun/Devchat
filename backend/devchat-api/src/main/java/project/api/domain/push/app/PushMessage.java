package project.api.domain.push.app;

/**
 * 휴대폰 알림 한 건. 서비스 워커(sw.js)가 이 JSON을 받아 알림을 띄운다.
 * url: 알림을 누르면 열 화면, tag: 같은 tag의 알림은 새것으로 바뀐다(쌓이지 않음)
 */
public record PushMessage(String title, String body, String url, String tag) {
}
