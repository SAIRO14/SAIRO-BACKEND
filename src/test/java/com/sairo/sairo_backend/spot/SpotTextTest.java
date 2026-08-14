package com.sairo.sairo_backend.spot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpotTextTest {

    // --- normalize: 실데이터에서 확인된 9가지 줄바꿈 변형을 \n 하나로 통일

    @Test
    void normalize_withBrTagVariants_unifiesToSingleNewline() {
        assertThat(SpotText.normalize("a<br>b")).isEqualTo("a\nb");
        assertThat(SpotText.normalize("a<br>\nb")).isEqualTo("a\nb");
        assertThat(SpotText.normalize("a<br> \nb")).isEqualTo("a\nb");
        assertThat(SpotText.normalize("a<br />\nb")).isEqualTo("a\nb");
        assertThat(SpotText.normalize("a<br/>\nb")).isEqualTo("a\nb");
        assertThat(SpotText.normalize("a\r\nb")).isEqualTo("a\nb");
    }

    @Test
    void normalize_withNullOrBlank_returnsNull() {
        assertThat(SpotText.normalize(null)).isNull();
        assertThat(SpotText.normalize("  ")).isNull();
        assertThat(SpotText.normalize("<br>")).isNull();
    }

    // --- hoursSummary

    @Test
    void hoursSummary_withAlwaysOpen_returnsTag() {
        assertThat(SpotText.hoursSummary("상시 개방")).isEqualTo("상시 개방");
        assertThat(SpotText.hoursSummary("24시간 개방")).isEqualTo("상시 개방");
        assertThat(SpotText.hoursSummary("연중 개방")).isEqualTo("상시 개방");
    }

    @Test
    void hoursSummary_withSingleTimeRange_returnsTime() {
        assertThat(SpotText.hoursSummary("09:00~18:00")).isEqualTo("09:00~18:00");
        assertThat(SpotText.hoursSummary("09:00 ~ 18:00 (입장 마감 17:00)")).isEqualTo("09:00~18:00");
    }

    @Test
    void hoursSummary_withSameTimeRepeated_returnsTime() {
        assertThat(SpotText.hoursSummary("- 평일 09:00~18:00<br>\n- 주말 09:00~18:00"))
                .isEqualTo("09:00~18:00");
    }

    @Test
    void hoursSummary_withAlwaysOpenAndSpecificTime_returnsNull() {
        // 야외는 상시·실내는 시간제인 값에 "상시 개방" 태그를 달면 오도한다 (한라수목원 사례)
        assertThat(SpotText.hoursSummary("[야외전시원]<br>\n- 상시 개방<br>\n[실내시설]<br>\n- 09:00~18:00")).isNull();
        assertThat(SpotText.hoursSummary("상시개방\n※ 방문자센터 09:00~18:00")).isNull();
    }

    @Test
    void hoursSummary_withMultipleDifferentTimes_returnsNull() {
        // 항차별·시설별 시간 중 첫 번째만 뽑으면 오독을 낳는다 (유람선 사례)
        assertThat(SpotText.hoursSummary("- 1항차 11:00~12:00<br>\n- 2항차 14:10~15:10")).isNull();
        assertThat(SpotText.hoursSummary("- 관람 09:00~21:30\n- 족욕 11:00~18:00")).isNull();
    }

    @Test
    void hoursSummary_withSectionLabel_returnsNull() {
        assertThat(SpotText.hoursSummary("[하절기(3월~10월)]<br>\n09:00~18:00")).isNull();
    }

    @Test
    void hoursSummary_withTextOnly_returnsNull() {
        assertThat(SpotText.hoursSummary("※ 일부 통제될 수 있으므로 방문 시 전화문의 요망")).isNull();
        assertThat(SpotText.hoursSummary(null)).isNull();
    }

    // --- closedDaysSummary

    @Test
    void closedDaysSummary_withYearRoundVariants_returnsTag() {
        assertThat(SpotText.closedDaysSummary("연중무휴")).isEqualTo("연중무휴");
        assertThat(SpotText.closedDaysSummary("연중 무휴")).isEqualTo("연중무휴");
        assertThat(SpotText.closedDaysSummary("연중개방")).isEqualTo("연중무휴");
        assertThat(SpotText.closedDaysSummary("없음")).isEqualTo("연중무휴");
    }

    @Test
    void closedDaysSummary_withYearRoundAndWeatherCaveat_returnsTag() {
        assertThat(SpotText.closedDaysSummary("연중무휴 (기상 악화 시 휴항)")).isEqualTo("연중무휴");
    }

    @Test
    void closedDaysSummary_withSingleWeekday_returnsTag() {
        assertThat(SpotText.closedDaysSummary("매주 월요일 / 1월1일 / 설·추석 연휴")).isEqualTo("월요일 휴무");
    }

    @Test
    void closedDaysSummary_withYearRoundButPartialClosure_returnsNull() {
        // 마을은 무휴·문화관은 월요일 휴무인 값에 "연중무휴" 태그를 달면 오도한다 (양동마을 사례)
        assertThat(SpotText.closedDaysSummary("- 마을 연중무휴<br>\n- 문화관 매주 월요일")).isNull();
    }

    @Test
    void closedDaysSummary_withWeekdayRange_returnsNull() {
        assertThat(SpotText.closedDaysSummary("매주 일요일~월요일 / 설날·추석 당일")).isNull();
        assertThat(SpotText.closedDaysSummary("매주 월·화요일")).isNull();
    }

    @Test
    void closedDaysSummary_withUnsummarizableText_returnsNull() {
        assertThat(SpotText.closedDaysSummary("기상악화 시 결항")).isNull();
        assertThat(SpotText.closedDaysSummary("매달 첫째 주 월요일")).isNull();
        assertThat(SpotText.closedDaysSummary(null)).isNull();
    }

    // --- parkingAvailable

    @Test
    void parkingAvailable_withSimpleAnswers_returnsBoolean() {
        assertThat(SpotText.parkingAvailable("가능")).isTrue();
        assertThat(SpotText.parkingAvailable("가능<br>요금 (무료)")).isTrue();
        assertThat(SpotText.parkingAvailable("200대 가능")).isTrue();
        assertThat(SpotText.parkingAvailable("불가능")).isFalse();
        assertThat(SpotText.parkingAvailable("불가 (인근 공영주차장 이용)")).isFalse();
    }

    @Test
    void parkingAvailable_withDescriptiveText_returnsNull() {
        assertThat(SpotText.parkingAvailable("경주국립공원 주차장(석굴암 주차장/유료)")).isNull();
        assertThat(SpotText.parkingAvailable(null)).isNull();
    }

    // --- contactPhone

    @Test
    void contactPhone_withVariousFormats_extractsFirstNumber() {
        assertThat(SpotText.contactPhone("055-282-7270")).isEqualTo("055-282-7270");
        assertThat(SpotText.contactPhone("제주관광정보센터 064-740-6000")).isEqualTo("064-740-6000");
        assertThat(SpotText.contactPhone("1670-1188")).isEqualTo("1670-1188");
        assertThat(SpotText.contactPhone("A안내소 064-740-6000<br>\nB사무소 064-728-1521"))
                .isEqualTo("064-740-6000");
    }

    @Test
    void contactPhone_withMalformedNumber_returnsNull() {
        assertThat(SpotText.contactPhone("영천시 시설관리공단 054–330-2764")).isNull(); // en-dash
        assertThat(SpotText.contactPhone(null)).isNull();
    }
}
