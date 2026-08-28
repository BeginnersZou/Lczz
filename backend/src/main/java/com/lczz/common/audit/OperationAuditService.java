package com.lczz.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class OperationAuditService {
    private final OperationAuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    public OperationAuditService(OperationAuditLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void recordSuccess(long operatorUserId, String operationType, String businessType, long businessId,
                              String requestId, String clientIp, Object before, Object after) {
        OperationAuditLogEntity log = new OperationAuditLogEntity();
        log.setOperatorUserId(operatorUserId);
        log.setOperationType(operationType);
        log.setBusinessType(businessType);
        log.setBusinessId(Long.toString(businessId));
        log.setRequestId(trimToLength(requestId, 64));
        log.setBeforeJson(toJson(before));
        log.setAfterJson(toJson(after));
        log.setResultCode("SUCCESS");
        log.setClientIp(trimToLength(clientIp, 64));
        log.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        mapper.insert(log);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit snapshot", exception);
        }
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
