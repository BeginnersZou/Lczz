package com.lczz.common.time;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Business wall times and database-generated DATETIME values use Beijing time. */
public final class BusinessTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final ZoneOffset OFFSET = ZoneOffset.ofHours(8);

    private BusinessTime() { }

    public static LocalDateTime fromUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZONE).toLocalDateTime();
    }
}
