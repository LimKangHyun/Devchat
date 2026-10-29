package project.api.domain.schedule.app;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleExclusion;

// 애플 캘린더 구독용 iCalendar(RFC 5545) 문자열을 만든다. 회차는 펼치지 않고 RRULE/EXDATE로 넘긴다
public final class IcsWriter {

    private static final String CRLF = "\r\n";
    private static final int MAX_OCTETS = 75;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String TZID = "Asia/Seoul";

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String[] DAY_CODES = {"MO", "TU", "WE", "TH", "FR", "SA", "SU"};

    private IcsWriter() {
    }

    /** now는 한국 시간이다 */
    public static String write(List<Schedule> schedules, LocalDateTime now) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR");
        line(out, "VERSION:2.0");
        line(out, "PRODID:-//DevChat//Calendar//KO");
        line(out, "CALSCALE:GREGORIAN");
        line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:DevChat");
        line(out, "X-WR-TIMEZONE:" + TZID);
        line(out, "REFRESH-INTERVAL;VALUE=DURATION:PT15M");
        line(out, "X-PUBLISHED-TTL:PT15M");

        line(out, "BEGIN:VTIMEZONE");
        line(out, "TZID:" + TZID);
        line(out, "BEGIN:STANDARD");
        line(out, "DTSTART:19700101T000000");
        line(out, "TZOFFSETFROM:+0900");
        line(out, "TZOFFSETTO:+0900");
        line(out, "TZNAME:KST");
        line(out, "END:STANDARD");
        line(out, "END:VTIMEZONE");

        String stamp = utc(now);
        for (Schedule s : schedules) {
            event(out, s, stamp);
        }

        line(out, "END:VCALENDAR");
        return out.toString();
    }

    private static void event(StringBuilder out, Schedule s, String stamp) {
        line(out, "BEGIN:VEVENT");
        line(out, "UID:schedule-" + s.getId() + "@devchat");
        line(out, "DTSTAMP:" + stamp);
        line(out, "LAST-MODIFIED:" + utc(s.getUpdatedAt()));
        line(out, "SEQUENCE:" + s.getSequence());

        if (s.isAllDay()) {
            line(out, "DTSTART;VALUE=DATE:" + DATE.format(s.getStartAt()));
            // 끝 날짜는 포함이 아니라 다음 날로 적는다
            line(out, "DTEND;VALUE=DATE:" + DATE.format(s.getEndAt().toLocalDate().plusDays(1)));
        } else {
            line(out, "DTSTART;TZID=" + TZID + ":" + LOCAL.format(s.getStartAt()));
            line(out, "DTEND;TZID=" + TZID + ":" + LOCAL.format(s.getEndAt()));
        }

        line(out, "SUMMARY:" + escape(s.getTitle()));
        if (hasText(s.getLocation())) {
            line(out, "LOCATION:" + escape(s.getLocation()));
        }
        String description = description(s);
        if (!description.isEmpty()) {
            line(out, "DESCRIPTION:" + escape(description));
        }

        RepeatFrequency f = s.getRepeatFrequency();
        if (f != null && f != RepeatFrequency.NONE) {
            line(out, "RRULE:" + rrule(s, f));
            List<LocalDate> excluded = s.getExclusions().stream()
                .map(ScheduleExclusion::getOccurrenceDate)
                .sorted()
                .toList();
            for (LocalDate date : excluded) {
                if (s.isAllDay()) {
                    line(out, "EXDATE;VALUE=DATE:" + DATE.format(date));
                } else {
                    line(out, "EXDATE;TZID=" + TZID + ":"
                        + LOCAL.format(date.atTime(s.getStartAt().toLocalTime())));
                }
            }
        }
        line(out, "END:VEVENT");
    }

    private static String rrule(Schedule s, RepeatFrequency f) {
        LocalDate start = s.getStartAt().toLocalDate();
        StringBuilder rule = new StringBuilder("FREQ=").append(f.name());
        switch (f) {
            case WEEKLY -> {
                Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
                if (s.getRepeatDays() != null) {
                    days.addAll(s.getRepeatDays());
                }
                if (days.isEmpty()) {
                    days.add(start.getDayOfWeek());
                }
                rule.append(";BYDAY=").append(days.stream()
                    .map(d -> DAY_CODES[d.getValue() - 1])
                    .collect(Collectors.joining(",")));
            }
            // 날짜를 못 박아야 애플도 없는 달(31일)·평년(2/29)을 건너뛴다
            case MONTHLY -> rule.append(";BYMONTHDAY=").append(start.getDayOfMonth());
            case YEARLY -> rule.append(";BYMONTH=").append(start.getMonthValue())
                .append(";BYMONTHDAY=").append(start.getDayOfMonth());
            default -> {
            }
        }
        // RFC 5545는 UNTIL과 COUNT를 같이 쓰지 못하게 한다. 둘 다 있으면 UNTIL을 쓴다
        if (s.getRepeatUntil() != null) {
            rule.append(";UNTIL=").append(s.isAllDay()
                ? DATE.format(s.getRepeatUntil())
                : utc(s.getRepeatUntil().atTime(23, 59, 59)));
        } else if (s.getRepeatCount() != null) {
            rule.append(";COUNT=").append(s.getRepeatCount());
        }
        return rule.toString();
    }

    // 메모 + 참여자 닉네임
    private static String description(Schedule s) {
        StringBuilder d = new StringBuilder();
        if (hasText(s.getMemo())) {
            d.append(s.getMemo());
        }
        List<String> names = s.getParticipants().stream()
            .map(p -> p.getMember().getNickname())
            .toList();
        if (!names.isEmpty()) {
            if (d.length() > 0) {
                d.append("\n\n");
            }
            d.append("참여자: ").append(String.join(", ", names));
        }
        return d.toString();
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }

    private static String utc(LocalDateTime kst) {
        return UTC.format(kst.atZone(KST).withZoneSameInstant(ZoneOffset.UTC));
    }

    /** TEXT 값 이스케이프: \ ; , 줄바꿈 */
    static String escape(String text) {
        return text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\r", "\\n")
            .replace("\n", "\\n");
    }

    // 한 줄을 75옥텟 이하로 접어서 CRLF와 함께 붙인다. 문자 중간에서 자르지 않는다
    private static void line(StringBuilder out, String content) {
        int octets = 0;
        int i = 0;
        while (i < content.length()) {
            int cp = content.codePointAt(i);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + size > MAX_OCTETS) {
                out.append(CRLF).append(' ');
                octets = 1; // 앞의 공백 한 칸
            }
            out.appendCodePoint(cp);
            octets += size;
            i += Character.charCount(cp);
        }
        out.append(CRLF);
    }
}
