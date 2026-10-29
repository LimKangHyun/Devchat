package project.api.domain.schedule.dto;

// 캘린더 구독 주소. webcalUrl은 애플 기기에서 바로 구독 창을 띄운다
public record FeedUrlResponse(String httpsUrl, String webcalUrl) {

    public static FeedUrlResponse of(String httpsUrl) {
        return new FeedUrlResponse(httpsUrl, httpsUrl.replaceFirst("^https?://", "webcal://"));
    }
}
