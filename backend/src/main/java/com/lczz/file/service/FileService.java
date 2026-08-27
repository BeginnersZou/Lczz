package com.lczz.file.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.file.config.FileStorageProperties;
import com.lczz.file.persistence.FileAssetRecord;
import com.lczz.file.persistence.FileAssetRecordMapper;
import com.lczz.file.persistence.FileRelationRecord;
import com.lczz.file.persistence.FileRelationRecordMapper;
import com.lczz.file.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private static final Map<String, String> EXTENSION_MIME = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png",
            "gif", "image/gif", "webp", "image/webp", "mp4", "video/mp4",
            "mov", "video/quicktime", "m4v", "video/mp4");
    private static final Set<String> BUSINESS_TYPES = Set.of("PRODUCT", "ORDER", "PROGRESS", "REVIEW");
    private static final Set<String> USAGE_TYPES = Set.of(
            "COVER", "DETAIL", "ATTACHMENT", "PROGRESS", "COMPLETION", "REVIEW");
    private static final Map<String, Set<String>> BUSINESS_USAGES = Map.of(
            "PRODUCT", Set.of("COVER", "DETAIL"),
            "ORDER", Set.of("ATTACHMENT"),
            "PROGRESS", Set.of("PROGRESS", "COMPLETION"),
            "REVIEW", Set.of("REVIEW"));
    private static final Map<String, Integer> USAGE_LIMITS = Map.of(
            "COVER", 1, "DETAIL", 9, "ATTACHMENT", 9,
            "PROGRESS", 9, "COMPLETION", 9, "REVIEW", 9);

    private final FileAssetRecordMapper fileMapper;
    private final FileRelationRecordMapper relationMapper;
    private final FileStorage storage;
    private final FileStorageProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final byte[] accessKey;

    public FileService(FileAssetRecordMapper fileMapper, FileRelationRecordMapper relationMapper,
                       FileStorage storage, FileStorageProperties properties, JdbcTemplate jdbcTemplate) {
        this.fileMapper = fileMapper;
        this.relationMapper = relationMapper;
        this.storage = storage;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.accessKey = createAccessKey(properties.getAccessSecret());
    }

    @Transactional
    public FileView upload(AuthenticatedUser actor, MultipartFile multipart, RelationCommand relation) {
        ValidatedFile file = validate(multipart);
        RelationCommand normalized = normalizeOptionalRelation(relation);
        if (normalized != null) authorizeBusiness(actor, normalized.businessType(), normalized.businessId(), true);
        String objectKey = newObjectKey(file.extension());
        boolean stored = false;
        try (InputStream source = multipart.getInputStream()) {
            MessageDigest digest = sha256Digest();
            long storedSize = storage.store(objectKey, new DigestInputStream(source, digest));
            stored = true;
            if (storedSize <= 0 || storedSize > file.maxBytes()) {
                throw new BusinessException(413, "FILE_TOO_LARGE",
                        file.mimeType().startsWith("image/")
                                ? "图片大小不能超过" + properties.getMaxImageBytes() + "字节"
                                : "视频大小不能超过" + properties.getMaxBytes() + "字节");
            }
            FileAssetRecord record = new FileAssetRecord();
            record.setStorageType(storage.storageType());
            record.setObjectKey(objectKey);
            record.setOriginalName(file.originalName());
            record.setMimeType(file.mimeType());
            record.setFileSize(storedSize);
            record.setSha256(HexFormat.of().formatHex(digest.digest()));
            record.setUploadedBy(actor.userId());
            record.setDeleted(false);
            fileMapper.insert(record);
            record.setAccessUrl("/api/files/" + record.getId());
            fileMapper.updateById(record);
            if (normalized != null) addRelation(actor, record.getId(), normalized);
            return toView(record, signedUrl(record.getId()));
        } catch (IOException exception) {
            storage.deleteQuietly(objectKey);
            throw new BusinessException(500, "FILE_STORAGE_FAILED", "文件保存失败，请稍后重试");
        } catch (RuntimeException exception) {
            if (stored) storage.deleteQuietly(objectKey);
            throw exception;
        }
    }

    @Transactional
    public FileView bind(AuthenticatedUser actor, long fileId, RelationCommand relation) {
        FileAssetRecord file = requireFile(fileId);
        if (!actor.hasRole(RoleCode.ADMIN) && !Objects.equals(file.getUploadedBy(), actor.userId())) {
            throw new BusinessException(403, "FILE_BIND_FORBIDDEN", "只能绑定自己上传的文件");
        }
        RelationCommand normalized = normalizeRequiredRelation(relation);
        authorizeBusiness(actor, normalized.businessType(), normalized.businessId(), true);
        addRelation(actor, fileId, normalized);
        return toView(file, signedUrl(fileId));
    }

    @Transactional(readOnly = true)
    public List<FileView> listBusinessFiles(AuthenticatedUser actor, RelationCommand relation) {
        RelationCommand normalized = normalizeRequiredRelation(relation);
        authorizeBusiness(actor, normalized.businessType(), normalized.businessId(), false);
        List<FileRelationRecord> relations = relationMapper.selectList(
                new LambdaQueryWrapper<FileRelationRecord>()
                        .eq(FileRelationRecord::getBusinessType, normalized.businessType())
                        .eq(FileRelationRecord::getBusinessId, normalized.businessId())
                        .eq(FileRelationRecord::getUsageType, normalized.usageType())
                        .orderByAsc(FileRelationRecord::getSortOrder)
                        .orderByAsc(FileRelationRecord::getId));
        if (relations.isEmpty()) return List.of();
        Map<Long, FileAssetRecord> files = fileMapper.selectBatchIds(relations.stream()
                        .map(FileRelationRecord::getFileId).collect(Collectors.toSet())).stream()
                .filter(file -> !Boolean.TRUE.equals(file.getDeleted()))
                .collect(Collectors.toMap(FileAssetRecord::getId, Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        return relations.stream().map(link -> files.get(link.getFileId()))
                .filter(Objects::nonNull)
                .map(file -> toView(file, signedUrl(file.getId())))
                .toList();
    }

    @Transactional
    public boolean unbind(AuthenticatedUser actor, long fileId, RelationCommand relation) {
        requireFile(fileId);
        RelationCommand normalized = normalizeRequiredRelation(relation);
        authorizeBusiness(actor, normalized.businessType(), normalized.businessId(), true);
        relationMapper.delete(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, normalized.businessType())
                .eq(FileRelationRecord::getBusinessId, normalized.businessId())
                .eq(FileRelationRecord::getUsageType, normalized.usageType())
                .eq(FileRelationRecord::getFileId, fileId));
        return true;
    }

    public FileContent authenticatedContent(AuthenticatedUser actor, long fileId) {
        FileAssetRecord file = requireFile(fileId);
        authorizeFile(actor, file);
        return new FileContent(file, storage.load(file.getObjectKey()));
    }

    public FileContent signedContent(long fileId, long expires, String signature) {
        verifySignature(fileId, expires, signature);
        FileAssetRecord file = requireFile(fileId);
        return new FileContent(file, storage.load(file.getObjectKey()));
    }

    public FileView issueAccess(AuthenticatedUser actor, long fileId) {
        FileAssetRecord file = requireFile(fileId);
        authorizeFile(actor, file);
        return toView(file, signedUrl(fileId));
    }

    private void addRelation(AuthenticatedUser actor, long fileId, RelationCommand relation) {
        Long duplicate = relationMapper.selectCount(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, relation.businessType())
                .eq(FileRelationRecord::getBusinessId, relation.businessId())
                .eq(FileRelationRecord::getUsageType, relation.usageType())
                .eq(FileRelationRecord::getFileId, fileId));
        if (duplicate > 0) return;
        Long count = relationMapper.selectCount(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, relation.businessType())
                .eq(FileRelationRecord::getBusinessId, relation.businessId())
                .eq(FileRelationRecord::getUsageType, relation.usageType()));
        int limit = USAGE_LIMITS.getOrDefault(relation.usageType(), 9);
        if (count >= limit) throw new BusinessException("FILE_COUNT_LIMIT", "该业务图片数量已达到上限" + limit);
        FileRelationRecord link = new FileRelationRecord();
        link.setBusinessType(relation.businessType());
        link.setBusinessId(relation.businessId());
        link.setUsageType(relation.usageType());
        link.setFileId(fileId);
        link.setSortOrder(relation.sortOrder() == null ? count.intValue() : relation.sortOrder());
        link.setCreatedBy(actor.userId());
        try { relationMapper.insert(link); }
        catch (DuplicateKeyException ignored) { }
    }

    private void authorizeFile(AuthenticatedUser actor, FileAssetRecord file) {
        if (actor.hasRole(RoleCode.ADMIN)) return;
        List<FileRelationRecord> relations = relationMapper.selectList(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getFileId, file.getId()));
        if (isProductCover(actor, file.getId())) return;
        if (!relations.isEmpty()) {
            boolean allowed = relations.stream().anyMatch(relation -> canAccessBusiness(
                    actor, relation.getBusinessType(), relation.getBusinessId(), false));
            if (!allowed) throw new BusinessException(403, "FILE_ACCESS_FORBIDDEN", "无权访问该文件");
            return;
        }
        if (!Objects.equals(file.getUploadedBy(), actor.userId())) {
            throw new BusinessException(403, "FILE_ACCESS_FORBIDDEN", "无权访问该文件");
        }
    }

    private boolean isProductCover(AuthenticatedUser actor, long fileId) {
        String sql = actor.hasRole(RoleCode.ADMIN)
                ? "SELECT COUNT(*) FROM product WHERE cover_file_id=? AND deleted=0"
                : "SELECT COUNT(*) FROM product WHERE cover_file_id=? AND deleted=0 AND enabled=1";
        return count(sql, fileId) > 0;
    }

    private void authorizeBusiness(AuthenticatedUser actor, String businessType, long businessId, boolean write) {
        if (!canAccessBusiness(actor, businessType, businessId, write)) {
            throw new BusinessException(403, "FILE_RELATION_FORBIDDEN", "无权关联或访问该业务文件");
        }
    }

    private boolean canAccessBusiness(AuthenticatedUser actor, String type, long id, boolean write) {
        if (actor.hasRole(RoleCode.ADMIN)) return businessExists(type, id);
        return switch (type) {
            case "PRODUCT" -> !write && count(
                    "SELECT COUNT(*) FROM product WHERE id=? AND deleted=0 AND enabled=1", id) > 0;
            case "ORDER" -> canAccessOrder(actor, id, write);
            case "PROGRESS" -> canAccessProgress(actor, id, write);
            case "REVIEW" -> canAccessReview(actor, id, write);
            default -> false;
        };
    }

    private boolean businessExists(String type, long id) {
        return switch (type) {
            case "PRODUCT" -> count("SELECT COUNT(*) FROM product WHERE id=? AND deleted=0", id) > 0;
            case "ORDER" -> count("SELECT COUNT(*) FROM work_order WHERE id=? AND deleted=0", id) > 0;
            case "PROGRESS" -> count("SELECT COUNT(*) FROM work_order_progress WHERE id=?", id) > 0;
            case "REVIEW" -> count("SELECT COUNT(*) FROM work_order_review WHERE id=?", id) > 0;
            default -> false;
        };
    }

    private boolean canAccessOrder(AuthenticatedUser actor, long orderId, boolean write) {
        if (actor.hasRole(RoleCode.INSTALLER)) {
            return count("SELECT COUNT(*) FROM work_order WHERE id=? AND installer_user_id=? AND deleted=0",
                    orderId, actor.userId()) > 0;
        }
        if (write) return false;
        return count("SELECT COUNT(*) FROM work_order WHERE id=? AND customer_user_id=? AND deleted=0",
                orderId, actor.userId()) > 0;
    }

    private boolean canAccessProgress(AuthenticatedUser actor, long progressId, boolean write) {
        if (actor.hasRole(RoleCode.INSTALLER)) {
            return count("SELECT COUNT(*) FROM work_order_progress p JOIN work_order o ON o.id=p.order_id "
                    + "WHERE p.id=? AND p.installer_user_id=? AND o.installer_user_id=? AND o.deleted=0",
                    progressId, actor.userId(), actor.userId()) > 0;
        }
        if (write) return false;
        return count("SELECT COUNT(*) FROM work_order_progress p JOIN work_order o ON o.id=p.order_id "
                + "WHERE p.id=? AND o.customer_user_id=? AND o.deleted=0", progressId, actor.userId()) > 0;
    }

    private boolean canAccessReview(AuthenticatedUser actor, long reviewId, boolean write) {
        if (write) {
            return count("SELECT COUNT(*) FROM work_order_review WHERE id=? AND reviewer_user_id=?",
                    reviewId, actor.userId()) > 0;
        }
        if (actor.hasRole(RoleCode.INSTALLER)) {
            return count("SELECT COUNT(*) FROM work_order_review r JOIN work_order o ON o.id=r.order_id "
                    + "WHERE r.id=? AND o.installer_user_id=? AND o.deleted=0", reviewId, actor.userId()) > 0;
        }
        return count("SELECT COUNT(*) FROM work_order_review r JOIN work_order o ON o.id=r.order_id "
                + "WHERE r.id=? AND o.customer_user_id=? AND o.deleted=0", reviewId, actor.userId()) > 0;
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private ValidatedFile validate(MultipartFile multipart) {
        if (multipart == null || multipart.isEmpty()) throw new BusinessException("EMPTY_FILE", "请选择需要上传的图片或视频");
        if (multipart.getSize() > properties.getMaxBytes()) {
            throw new BusinessException(413, "FILE_TOO_LARGE", "文件大小不能超过" + properties.getMaxBytes() + "字节");
        }
        byte[] header;
        try (InputStream input = multipart.getInputStream()) { header = input.readNBytes(16); }
        catch (IOException exception) { throw new BusinessException(400, "FILE_READ_FAILED", "无法读取上传文件"); }
        if (header.length == 0) {
            throw new BusinessException(413, "FILE_TOO_LARGE", "上传文件为空或超过大小限制");
        }
        String originalName = leafName(multipart.getOriginalFilename());
        String extension = extension(originalName);
        String detected = detectMime(header);
        String expected = EXTENSION_MIME.get(extension);
        String claimed = normalizeMime(multipart.getContentType());
        if (expected == null || !expected.equals(detected) || !detected.equals(claimed)) {
            throw new BusinessException("INVALID_FILE_TYPE", "仅支持 jpg/png/gif/webp 图片或 mp4/mov/m4v 视频，且文件类型必须真实一致");
        }
        long typeLimit = detected.startsWith("image/")
                ? Math.min(properties.getMaxBytes(), properties.getMaxImageBytes())
                : properties.getMaxBytes();
        if (multipart.getSize() > typeLimit) {
            throw new BusinessException(413, "FILE_TOO_LARGE", detected.startsWith("image/")
                    ? "图片大小不能超过" + properties.getMaxImageBytes() + "字节"
                    : "视频大小不能超过" + properties.getMaxBytes() + "字节");
        }
        return new ValidatedFile(originalName, extension, detected, typeLimit);
    }

    private String detectMime(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 6) {
            String prefix = new String(bytes, 0, 6, StandardCharsets.US_ASCII);
            if ("GIF87a".equals(prefix) || "GIF89a".equals(prefix)) return "image/gif";
        }
        if (bytes.length >= 12 && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) return "image/webp";
        if (bytes.length >= 12 && "ftyp".equals(new String(bytes, 4, 4, StandardCharsets.US_ASCII))) {
            String brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
            if ("qt  ".equals(brand)) return "video/quicktime";
            return "video/mp4";
        }
        return "application/octet-stream";
    }

    private String normalizeMime(String mime) {
        if (mime == null) return "";
        String value = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(value)) return "image/jpeg";
        if ("video/x-m4v".equals(value)) return "video/mp4";
        return value;
    }

    private String leafName(String raw) {
        String value = raw == null ? "" : raw.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isBlank() || value.length() > 255) throw new BusinessException("INVALID_FILE_NAME", "文件名不合法");
        return value;
    }

    private String extension(String name) {
        int index = name.lastIndexOf('.');
        return index < 1 || index == name.length() - 1 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private RelationCommand normalizeOptionalRelation(RelationCommand relation) {
        if (relation == null || relation.businessType() == null && relation.businessId() == null && relation.usageType() == null) {
            return null;
        }
        return normalizeRequiredRelation(relation);
    }

    private RelationCommand normalizeRequiredRelation(RelationCommand relation) {
        if (relation == null || relation.businessType() == null || relation.businessId() == null
                || relation.usageType() == null || relation.businessId() < 1) {
            throw new BusinessException("INVALID_FILE_RELATION", "业务类型、业务 ID 和用途必须同时提供");
        }
        String type = relation.businessType().trim().toUpperCase(Locale.ROOT);
        String usage = relation.usageType().trim().toUpperCase(Locale.ROOT);
        if (!BUSINESS_TYPES.contains(type) || !USAGE_TYPES.contains(usage)) {
            throw new BusinessException("INVALID_FILE_RELATION", "文件业务类型或用途不合法");
        }
        if (!BUSINESS_USAGES.get(type).contains(usage)) {
            throw new BusinessException("INVALID_FILE_RELATION", "文件用途与业务类型不匹配");
        }
        return new RelationCommand(type, relation.businessId(), usage, relation.sortOrder());
    }

    private FileAssetRecord requireFile(long id) {
        FileAssetRecord file = fileMapper.selectById(id);
        if (file == null || Boolean.TRUE.equals(file.getDeleted())) {
            throw new BusinessException(404, "FILE_NOT_FOUND", "文件不存在");
        }
        return file;
    }

    private String newObjectKey(String extension) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return "%04d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);
    }

    private MessageDigest sha256Digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }

    private FileView toView(FileAssetRecord file, String url) {
        return new FileView(file.getId(), file.getOriginalName(), file.getMimeType(),
                file.getFileSize() == null ? 0L : file.getFileSize(),
                file.getSha256(), url, file.getCreatedAt());
    }

    private String signedUrl(long fileId) {
        long expires = Instant.now().plusSeconds(Math.max(1, properties.getSignedUrlMinutes()) * 60).getEpochSecond();
        String signature = signature(fileId, expires);
        return "/api/files/access/%d?expires=%d&signature=%s".formatted(fileId, expires, signature);
    }

    private void verifySignature(long fileId, long expires, String supplied) {
        if (supplied == null || expires < Instant.now().getEpochSecond()) {
            throw new BusinessException(403, "FILE_URL_EXPIRED", "文件访问地址已过期");
        }
        byte[] expected = signature(fileId, expires).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = supplied.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(403, "INVALID_FILE_SIGNATURE", "文件访问签名无效");
        }
    }

    private String signature(long fileId, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(accessKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal((fileId + ":" + expires).getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }

    private byte[] createAccessKey(String configured) {
        if (configured != null && configured.getBytes(StandardCharsets.UTF_8).length >= 32) {
            return configured.getBytes(StandardCharsets.UTF_8);
        }
        byte[] generated = new byte[32];
        new SecureRandom().nextBytes(generated);
        return generated;
    }

    private record ValidatedFile(String originalName, String extension, String mimeType, long maxBytes) { }
    public record RelationCommand(String businessType, Long businessId, String usageType, Integer sortOrder) { }
    public record FileView(long id, String originalName, String mimeType, long size, String sha256,
                           String url, java.time.LocalDateTime createdAt) { }
    public record FileContent(FileAssetRecord metadata, Resource resource) { }
}
