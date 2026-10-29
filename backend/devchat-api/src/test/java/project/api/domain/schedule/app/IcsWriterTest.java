package project.api.domain.schedule.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import project.api.domain.member.entity.Member;
import project.api.domain.schedule.entity.RepeatFrequency;
import project.api.domain.schedule.entity.Schedule;
import project.api.domain.schedule.entity.ScheduleDraft;

class IcsWriterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);
    private final Member owner = Member.builder().id(1L).nickname("민수").build();

    private Schedule schedule(long id, String title, LocalDateTime start, LocalDateTime end,
                              boolean allDay, RepeatFrequency f, Set<DayOfWeek> days,
                              LocalDate until, Integer count) {
        ScheduleDraft d = new ScheduleDraft(title, null, null, start, end, allDay,
            f, days, until, count, List.of());
        Schedule s = Schedule.create(owner, d, NOW);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private static LocalDateTime at(int y, int m, int d, int h) {
        return LocalDateTime.of(y, m, d, h, 0);
    }

    // 접힌 줄을 다시 펼친 줄 목록
    private static List<String> unfoldedLines(String ics) {
        return Arrays.asList(ics.replace("\r\n ", "").split("\r\n"));
    }

    @Test
    void timedEvent_hasTzidAndUid() {
        Schedule s = schedule(7, "회의", at(2026, 11, 3, 15), at(2026, 11, 3, 16), false,
            RepeatFrequency.NONE, null, null, null);

        String ics = IcsWriter.write(List.of(s), NOW);
        List<String> lines = unfoldedLines(ics);

        assertThat(lines).startsWith("BEGIN:VCALENDAR", "VERSION:2.0",
            "PRODID:-//DevChat//Calendar//KO");
        assertThat(lines).contains("CALSCALE:GREGORIAN", "METHOD:PUBLISH", "X-WR-CALNAME:DevChat",
            "X-WR-TIMEZONE:Asia/Seoul", "REFRESH-INTERVAL;VALUE=DURATION:PT15M",
            "X-PUBLISHED-TTL:PT15M", "BEGIN:VTIMEZONE", "TZID:Asia/Seoul", "BEGIN:STANDARD",
            "TZOFFSETFROM:+0900", "TZOFFSETTO:+0900", "TZNAME:KST", "DTSTART:19700101T000000",
            "END:STANDARD", "END:VTIMEZONE");
        assertThat(lines).contains("BEGIN:VEVENT", "UID:schedule-7@devchat",
            "DTSTAMP:20261029T030000Z", "LAST-MODIFIED:20261029T030000Z", "SEQUENCE:0",
            "DTSTART;TZID=Asia/Seoul:20261103T150000", "DTEND;TZID=Asia/Seoul:20261103T160000",
            "SUMMARY:회의", "END:VEVENT");
        assertThat(lines.get(lines.size() - 1)).isEqualTo("END:VCALENDAR");
        assertThat(ics).doesNotContain("RRULE", "VALARM");
    }

    @Test
    void allDay_endIsExclusiveNextDay() {
        Schedule s = schedule(1, "워크숍", at(2026, 11, 3, 0), at(2026, 11, 4, 0), true,
            RepeatFrequency.NONE, null, null, null);

        List<String> lines = unfoldedLines(IcsWriter.write(List.of(s), NOW));

        assertThat(lines).contains("DTSTART;VALUE=DATE:20261103", "DTEND;VALUE=DATE:20261105");
    }

    @Test
    void weekly_rruleByDayAndUntilUtc() {
        Schedule s = schedule(1, "스터디", at(2026, 11, 2, 19), at(2026, 11, 2, 20), false,
            RepeatFrequency.WEEKLY, Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY),
            LocalDate.of(2026, 12, 31), null);
        Schedule allDay = schedule(2, "청소", at(2026, 11, 2, 0), at(2026, 11, 2, 0), true,
            RepeatFrequency.WEEKLY, Set.of(DayOfWeek.MONDAY), LocalDate.of(2026, 12, 28), null);
        Schedule counted = schedule(3, "운동", at(2026, 11, 4, 7), at(2026, 11, 4, 8), false,
            RepeatFrequency.WEEKLY, Set.of(), null, 5);

        List<String> lines = unfoldedLines(IcsWriter.write(List.of(s, allDay, counted), NOW));

        // 23:59:59 KST = 14:59:59 UTC
        assertThat(lines).contains(
            "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261231T145959Z",
            "RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261228",
            "RRULE:FREQ=WEEKLY;BYDAY=WE;COUNT=5");
    }

    @Test
    void monthly_hasByMonthDay() {
        Schedule monthly = schedule(1, "정산", at(2026, 1, 31, 10), at(2026, 1, 31, 11), false,
            RepeatFrequency.MONTHLY, null, null, null);
        Schedule yearly = schedule(2, "기념일", at(2028, 2, 29, 0), at(2028, 2, 29, 0), true,
            RepeatFrequency.YEARLY, null, null, 3);
        Schedule daily = schedule(3, "물", at(2026, 11, 1, 9), at(2026, 11, 1, 10), false,
            RepeatFrequency.DAILY, null, null, null);

        List<String> lines = unfoldedLines(IcsWriter.write(List.of(monthly, yearly, daily), NOW));

        assertThat(lines).contains(
            "RRULE:FREQ=MONTHLY;BYMONTHDAY=31",
            "RRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29;COUNT=3",
            "RRULE:FREQ=DAILY");
    }

    @Test
    void exdate_sameFormatAsStart() {
        Schedule timed = schedule(1, "스터디", at(2026, 11, 2, 19), at(2026, 11, 2, 20), false,
            RepeatFrequency.WEEKLY, Set.of(DayOfWeek.MONDAY), null, null);
        timed.exclude(LocalDate.of(2026, 11, 9));
        timed.exclude(LocalDate.of(2026, 11, 16));
        Schedule allDay = schedule(2, "청소", at(2026, 11, 2, 0), at(2026, 11, 2, 0), true,
            RepeatFrequency.DAILY, null, null, null);
        allDay.exclude(LocalDate.of(2026, 11, 5));

        List<String> lines = unfoldedLines(IcsWriter.write(List.of(timed, allDay), NOW));

        assertThat(lines).contains(
            "EXDATE;TZID=Asia/Seoul:20261109T190000",
            "EXDATE;TZID=Asia/Seoul:20261116T190000",
            "EXDATE;VALUE=DATE:20261105");
    }

    @Test
    void escapesAndFoldsLongKoreanLines() {
        String title = "스프린트 회의, 1차; 백엔드\\프론트\n같이 보는 아주 긴 한국어 제목입니다 정말로 길어서 접혀야 합니다";
        Member friend = Member.builder().id(2L).nickname("지수").build();
        ScheduleDraft d = new ScheduleDraft(title, "회의실 A, 3층", "준비물: 노트북",
            at(2026, 11, 3, 15), at(2026, 11, 3, 16), false,
            RepeatFrequency.NONE, null, null, null, List.of());
        Schedule s = Schedule.create(owner, d, NOW);
        ReflectionTestUtils.setField(s, "id", 9L);
        s.replaceParticipants(List.of(friend));

        String ics = IcsWriter.write(List.of(s), NOW);

        for (String line : ics.split("\r\n")) {
            assertThat(line.getBytes(StandardCharsets.UTF_8).length)
                .as(line).isLessThanOrEqualTo(75);
        }
        List<String> lines = unfoldedLines(ics);
        String escaped = "스프린트 회의\\, 1차\\; 백엔드\\\\프론트\\n같이 보는 아주 긴 한국어 제목입니다 정말로 길어서 접혀야 합니다";
        assertThat(lines).contains("SUMMARY:" + escaped, "LOCATION:회의실 A\\, 3층",
            "DESCRIPTION:준비물: 노트북\\n\\n참여자: 지수");
        // 접힌 줄이 실제로 있다 (멀티바이트 문자가 깨지지 않았는지는 펼친 값으로 확인)
        assertThat(ics).contains("\r\n ");
        assertThat(ics).doesNotContain("�");
    }

    @Test
    void escape_stripsControlChars_keepsTabAndNewline() {
        // TEXT 값에 제어 문자가 있으면 캘린더 앱이 피드를 못 읽는다
        String raw = "a\u0000b\u0007c\u000Bd\u000Ce\u001Bf\u007Fg\th\ni\r\nj";

        assertThat(IcsWriter.escape(raw)).isEqualTo("abcdefg\th\\ni\\nj");
    }

    @Test
    void crlfLineEndings() {
        Schedule s = schedule(1, "회의", at(2026, 11, 3, 15), at(2026, 11, 3, 16), false,
            RepeatFrequency.NONE, null, null, null);

        String ics = IcsWriter.write(List.of(s), NOW);

        assertThat(ics).endsWith("END:VCALENDAR\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n", "\r");
    }
}
