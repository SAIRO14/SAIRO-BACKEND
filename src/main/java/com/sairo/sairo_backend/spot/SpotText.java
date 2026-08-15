package com.sairo.sairo_backend.spot;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TourAPI 원문 텍스트 필드의 줄바꿈 정규화와 태그용 요약 파생. (#74)
 *
 * 요약 규칙의 원칙은 "틀린 요약보다 null"이다. 한 필드에 여러 대상(시설·항차·절기)의
 * 정보가 섞인 값은 태그 하나가 전체를 대표하는 것처럼 보여 사용자를 오도하므로
 * 요약하지 않는다. null은 "요약 불가, 원문을 보라"는 계약이다.
 */
public final class SpotText {

    private static final Pattern NEWLINE = Pattern.compile("([ \\t]*<br\\s*/?>\\s*|[ \\t]*\\r?\\n)+");
    private static final Pattern TIME_RANGE = Pattern.compile("\\d{1,2}:\\d{2}\\s*~\\s*\\d{1,2}:\\d{2}");
    private static final Pattern ALWAYS_OPEN = Pattern.compile("(상시|24시간)\\s*(개방|무휴|영업|운영)?|연중\\s*(개방|무휴)");
    private static final Pattern YEAR_ROUND = Pattern.compile("연중\\s*(무휴|개방|상시)");
    private static final Pattern PARTIAL_CLOSURE = Pattern.compile("매주|휴무일|휴관");
    // 요일 나열(~ · , 또는 연속 요일)이 뒤따르면 단일 요일 요약이 오독을 낳으므로 매칭하지 않는다
    private static final Pattern WEEKLY_CLOSED = Pattern.compile("매주\\s*([월화수목금토일])요일(?!\\s*[~·,]|\\s*[월화수목금토일])");
    private static final Pattern PHONE = Pattern.compile("\\d{2,4}-\\d{3,4}-\\d{4}|1\\d{3}-\\d{4}");
    private static final Pattern PARKING_NO = Pattern.compile("^(불가능|불가|없음|주차\\s*불가)");
    private static final Pattern PARKING_YES = Pattern.compile("^(가능|있음|주차\\s*가능|주차장)");
    private static final Pattern PARKING_COUNT = Pattern.compile("\\d+\\s*대\\s*(까지)?\\s*(주차|진입)?\\s*가능");

    private SpotText() {
    }

    /** {@code <br>} 계열 태그와 raw 개행의 혼재(9가지 변형 확인됨)를 {@code \n} 하나로 통일한다. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = NEWLINE.matcher(raw).replaceAll("\n").strip();
        return normalized.isEmpty() ? null : normalized;
    }

    /** "상시 개방" 또는 단일 {@code HH:MM~HH:MM}. 시간이 여러 개거나 구간 라벨이 있으면 null. */
    public static String hoursSummary(String raw) {
        String text = normalize(raw);
        if (text == null) {
            return null;
        }
        Set<String> times = distinctTimeRanges(text);
        boolean alwaysOpen = ALWAYS_OPEN.matcher(text).find();
        boolean labeled = text.startsWith("[");
        if (alwaysOpen) {
            // 상시 개방 문구와 구체 시간·시설 라벨이 병기된 값은 일부 시설 얘기일 수 있다
            return (times.isEmpty() && !labeled) ? "상시 개방" : null;
        }
        if (labeled || times.size() != 1) {
            return null;
        }
        return times.iterator().next();
    }

    /** "연중무휴" 또는 "X요일 휴무". 시설별 예외나 요일 나열이 있으면 null. */
    public static String closedDaysSummary(String raw) {
        String text = normalize(raw);
        if (text == null) {
            return null;
        }
        if (YEAR_ROUND.matcher(text).find()) {
            return PARTIAL_CLOSURE.matcher(text).find() ? null : "연중무휴";
        }
        if (text.equals("없음") || text.equals("무휴")) {
            return "연중무휴";
        }
        Matcher weekly = WEEKLY_CLOSED.matcher(text);
        return weekly.find() ? weekly.group(1) + "요일 휴무" : null;
    }

    /** 가능/불가능 판정. 설명형(주차장 위치 안내 등)은 null. */
    public static Boolean parkingAvailable(String raw) {
        String text = normalize(raw);
        if (text == null) {
            return null;
        }
        if (PARKING_NO.matcher(text).find()) {
            return false;
        }
        if (PARKING_YES.matcher(text).find() || PARKING_COUNT.matcher(text).find()) {
            return true;
        }
        return null;
    }

    /** 첫 전화번호 추출. 지역번호형(055-282-7270)과 대표번호형(1670-1188)을 인식한다. */
    public static String contactPhone(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = PHONE.matcher(raw);
        return m.find() ? m.group() : null;
    }

    private static Set<String> distinctTimeRanges(String text) {
        Set<String> times = new LinkedHashSet<>();
        Matcher m = TIME_RANGE.matcher(text);
        while (m.find()) {
            times.add(m.group().replaceAll("\\s", ""));
        }
        return times;
    }
}
