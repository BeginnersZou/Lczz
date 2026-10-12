package com.lczz.common.time;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessTimeJsonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer businessTimeSerialization() {
        return builder -> {
            // Database-generated and appointment times are Beijing wall times. UTC fields opt in via @UtcTime.
            builder.serializerByType(LocalDateTime.class, new BusinessDateTimeSerializer());
            builder.serializerByType(OffsetDateTime.class, new StdSerializer<>(OffsetDateTime.class) {
                @Override
                public void serialize(OffsetDateTime value, JsonGenerator generator, SerializerProvider provider)
                        throws IOException {
                    generator.writeString(value.atZoneSameInstant(BusinessTime.ZONE)
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                }
            });
        };
    }
}
