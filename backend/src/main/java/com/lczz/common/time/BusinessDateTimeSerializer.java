package com.lczz.common.time;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Adds a zone to a stored wall time without changing the persisted DATETIME value. */
public class BusinessDateTimeSerializer extends StdSerializer<LocalDateTime> {
    private final ZoneId sourceZone;

    public BusinessDateTimeSerializer() { this(BusinessTime.ZONE); }

    protected BusinessDateTimeSerializer(ZoneId sourceZone) {
        super(LocalDateTime.class);
        this.sourceZone = sourceZone;
    }

    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeString(value.atZone(sourceZone).withZoneSameInstant(BusinessTime.ZONE)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    public static final class Utc extends BusinessDateTimeSerializer {
        public Utc() { super(ZoneOffset.UTC); }
    }
}
