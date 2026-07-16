package com.blog.blog_literario.dto.posts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReadingTimeBucketTest {

    @Test
    void bucketBoundsDeriveFromTheSharedReadingTimeConstant() {
        assertThat(ReadingTimeBucket.SHORT.minWords()).isEqualTo(0);
        assertThat(ReadingTimeBucket.SHORT.maxWords()).isEqualTo(800);
        assertThat(ReadingTimeBucket.MEDIUM.minWords()).isEqualTo(801);
        assertThat(ReadingTimeBucket.MEDIUM.maxWords()).isEqualTo(3000);
        assertThat(ReadingTimeBucket.LONG.minWords()).isEqualTo(3001);
        assertThat(ReadingTimeBucket.LONG.maxWords()).isNull();
    }

    @Test
    void from_knownKeysCaseInsensitive() {
        assertThat(ReadingTimeBucket.from("short")).isEqualTo(ReadingTimeBucket.SHORT);
        assertThat(ReadingTimeBucket.from("MEDIUM")).isEqualTo(ReadingTimeBucket.MEDIUM);
        assertThat(ReadingTimeBucket.from(" Long ")).isEqualTo(ReadingTimeBucket.LONG);
    }

    @Test
    void from_nullBlankOrUnknown_returnsNullMeaningNoFilter() {
        assertThat(ReadingTimeBucket.from(null)).isNull();
        assertThat(ReadingTimeBucket.from("  ")).isNull();
        assertThat(ReadingTimeBucket.from("bogus")).isNull();
    }
}
