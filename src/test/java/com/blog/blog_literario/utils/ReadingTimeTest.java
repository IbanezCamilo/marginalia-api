package com.blog.blog_literario.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReadingTimeTest {

    @Test
    void minutesFor_null_returnsOneMinuteFloor() {
        assertThat(ReadingTime.minutesFor(null)).isEqualTo(1);
    }

    @Test
    void minutesFor_zeroWords_returnsOneMinuteFloor() {
        assertThat(ReadingTime.minutesFor(0)).isEqualTo(1);
    }

    @Test
    void minutesFor_roundsUpPartialMinutes() {
        assertThat(ReadingTime.minutesFor(201)).isEqualTo(2);
    }

    @Test
    void minutesFor_exactMultiple_isExact() {
        // 800 words at 200 wpm = exactly 4 minutes (the SHORT bucket edge)
        assertThat(ReadingTime.minutesFor(800)).isEqualTo(4);
        assertThat(ReadingTime.minutesFor(3000)).isEqualTo(15);
    }

    @Test
    void minutesFor_oneWordPastTheEdge_crossesToNextMinute() {
        assertThat(ReadingTime.minutesFor(801)).isEqualTo(5);
        assertThat(ReadingTime.minutesFor(3001)).isEqualTo(16);
    }

    @Test
    void maxWordsFor_isTheInverseBoundary() {
        assertThat(ReadingTime.maxWordsFor(4)).isEqualTo(800);
        assertThat(ReadingTime.maxWordsFor(15)).isEqualTo(3000);
    }
}
