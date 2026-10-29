package com.memolake.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test clock: every read moves time forward by one millisecond, so consecutive writes get distinct,
 * strictly increasing timestamps. Tests can also set the time explicitly.
 */
public class MutableClock extends Clock {

    public static final Instant START = Instant.parse("2026-10-27T00:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);

    @Override
    public Instant instant() {
        return now.getAndUpdate(t -> t.plusMillis(1));
    }

    public void set(Instant instant) {
        now.set(instant);
    }

    public void advance(Duration duration) {
        now.updateAndGet(t -> t.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
