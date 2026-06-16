package com.blog.blog_literario.security;

import java.time.Duration;

enum RateLimitProfile {
    AUTH(10, Duration.ofMinutes(1)),
    PUBLIC(60, Duration.ofMinutes(1)),
    IMAGES(30, Duration.ofMinutes(1)),
    UPLOAD(10, Duration.ofHours(1));

    final long capacity;
    final Duration refillPeriod;

    RateLimitProfile(long capacity, Duration refillPeriod) {
        this.capacity = capacity;
        this.refillPeriod = refillPeriod;
    }
}
