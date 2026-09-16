package com.lczz.projectcase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.exception.BusinessException;
import com.lczz.file.persistence.FileAssetRecord;
import com.lczz.file.persistence.FileAssetRecordMapper;
import com.lczz.file.persistence.FileRelationRecord;
import com.lczz.file.persistence.FileRelationRecordMapper;
import com.lczz.file.service.FileService;
import com.lczz.projectcase.persistence.ProjectCaseEntity;
import com.lczz.projectcase.persistence.ProjectCaseMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectCaseService {
    public static final String BUSINESS_TYPE = "CASE";
    public static final String IMAGE_USAGE = "CASE";
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final ProjectCaseMapper caseMapper;
    private final FileAssetRecordMapper fileMapper;
    private final FileRelationRecordMapper relationMapper;
    private final FileService fileService;

    public ProjectCaseService(ProjectCaseMapper caseMapper, FileAssetRecordMapper fileMapper,
                              FileRelationRecordMapper relationMapper, FileService fileService) {
        this.caseMapper = caseMapper;
        this.fileMapper = fileMapper;
        this.relationMapper = relationMapper;
        this.fileService = fileService;
    }

    public ProjectCasePage list(AuthenticatedUser actor, int page, int pageSize, String keyword) {
        LambdaQueryWrapper<ProjectCaseEntity> query = new LambdaQueryWrapper<ProjectCaseEntity>()
                .like(keyword != null && !keyword.isBlank(), ProjectCaseEntity::getSiteName, keyword == null ? null : keyword.trim())
                .orderByDesc(ProjectCaseEntity::getUpdatedAt)
                .orderByDesc(ProjectCaseEntity::getId);
        Page<ProjectCaseEntity> result = caseMapper.selectPage(new Page<>(page, pageSize), query);
        return new ProjectCasePage(toSummaryViews(actor, result.getRecords()), result.getTotal(), page, pageSize);
    }

    public ProjectCaseDetailView detail(AuthenticatedUser actor, long id) {
        ProjectCaseEntity projectCase = requireCase(id);
        return toDetailView(actor, projectCase);
    }

    @Transactional
    public ProjectCaseDetailView create(AuthenticatedUser actor, ProjectCaseCommand command) {
        List<Long> imageIds = validateImages(command.imageFileIds());
        ProjectCaseEntity projectCase = new ProjectCaseEntity();
        projectCase.setSiteName(command.siteName().trim());
        projectCase.setCreatedBy(actor.userId());
        projectCase.setUpdatedBy(actor.userId());
        projectCase.setDeleted(false);
        caseMapper.insert(projectCase);
        replaceImages(projectCase.getId(), imageIds, actor.userId());
        return toDetailView(actor, projectCase);
    }

    @Transactional
    public ProjectCaseDetailView update(AuthenticatedUser actor, long id, ProjectCaseCommand command) {
        ProjectCaseEntity projectCase = requireCase(id);
        List<Long> imageIds = validateImages(command.imageFileIds());
        projectCase.setSiteName(command.siteName().trim());
        projectCase.setUpdatedBy(actor.userId());
        caseMapper.updateById(projectCase);
        replaceImages(id, imageIds, actor.userId());
        return toDetailView(actor, projectCase);
    }

    @Transactional
    public void delete(long id) {
        requireCase(id);
        caseMapper.deleteById(id);
        fileService.deleteBusinessFiles(BUSINESS_TYPE, List.of(id));
    }

    private List<ProjectCaseSummaryView> toSummaryViews(AuthenticatedUser actor, List<ProjectCaseEntity> projectCases) {
        if (projectCases.isEmpty()) return List.of();
        Map<Long, List<FileView>> images = loadImages(actor, projectCases.stream().map(ProjectCaseEntity::getId).toList());
        return projectCases.stream().map(projectCase -> {
            List<FileView> files = images.getOrDefault(projectCase.getId(), List.of());
            return new ProjectCaseSummaryView(projectCase.getId(), projectCase.getSiteName(),
                    files.isEmpty() ? null : files.getFirst(), projectCase.getUpdatedAt());
        }).toList();
    }

    private ProjectCaseDetailView toDetailView(AuthenticatedUser actor, ProjectCaseEntity projectCase) {
        List<FileView> images = loadImages(actor, List.of(projectCase.getId())).getOrDefault(projectCase.getId(), List.of());
        return new ProjectCaseDetailView(projectCase.getId(), projectCase.getSiteName(), images,
                projectCase.getCreatedAt(), projectCase.getUpdatedAt());
    }

    private Map<Long, List<FileView>> loadImages(AuthenticatedUser actor, Collection<Long> caseIds) {
        if (caseIds.isEmpty()) return Map.of();
        List<FileRelationRecord> relations = relationMapper.selectList(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, BUSINESS_TYPE)
                .eq(FileRelationRecord::getUsageType, IMAGE_USAGE)
                .in(FileRelationRecord::getBusinessId, caseIds)
                .orderByAsc(FileRelationRecord::getSortOrder)
                .orderByAsc(FileRelationRecord::getId));
        if (relations.isEmpty()) return Map.of();
        Map<Long, FileAssetRecord> files = fileMapper.selectBatchIds(relations.stream()
                        .map(FileRelationRecord::getFileId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(FileAssetRecord::getId, Function.identity()));
        Map<Long, List<FileView>> result = new java.util.HashMap<>();
        for (FileRelationRecord relation : relations) {
            FileAssetRecord file = files.get(relation.getFileId());
            if (file != null) {
                result.computeIfAbsent(relation.getBusinessId(), ignored -> new ArrayList<>())
                        .add(new FileView(file.getId(), fileService.issueAccess(actor, file.getId()).url()));
            }
        }
        return result;
    }

    private List<Long> validateImages(List<Long> rawFileIds) {
        List<Long> ids = rawFileIds == null ? List.of() : rawFileIds.stream()
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
        if (ids.isEmpty()) throw new BusinessException("CASE_IMAGES_REQUIRED", "请至少上传一张案例图片");
        List<FileAssetRecord> files = fileMapper.selectBatchIds(ids);
        if (files.size() != ids.size()) throw new BusinessException("FILE_NOT_FOUND", "存在无效或已删除的案例图片");
        for (FileAssetRecord file : files) {
            if (file.getMimeType() == null || !file.getMimeType().startsWith("image/")) {
                throw new BusinessException("CASE_IMAGE_TYPE_INVALID", "案例仅支持图片文件");
            }
            if (file.getFileSize() == null || file.getFileSize() > MAX_IMAGE_BYTES) {
                throw new BusinessException(413, "CASE_IMAGE_TOO_LARGE", "单张案例图片不能超过 10 MB");
            }
        }
        return ids;
    }

    private void replaceImages(long caseId, List<Long> imageIds, long actorId) {
        relationMapper.delete(new LambdaQueryWrapper<FileRelationRecord>()
                .eq(FileRelationRecord::getBusinessType, BUSINESS_TYPE)
                .eq(FileRelationRecord::getBusinessId, caseId)
                .eq(FileRelationRecord::getUsageType, IMAGE_USAGE));
        for (int index = 0; index < imageIds.size(); index++) {
            FileRelationRecord relation = new FileRelationRecord();
            relation.setBusinessType(BUSINESS_TYPE);
            relation.setBusinessId(caseId);
            relation.setUsageType(IMAGE_USAGE);
            relation.setFileId(imageIds.get(index));
            relation.setSortOrder(index);
            relation.setCreatedBy(actorId);
            relationMapper.insert(relation);
        }
    }

    private ProjectCaseEntity requireCase(long id) {
        ProjectCaseEntity projectCase = caseMapper.selectById(id);
        if (projectCase == null) throw new BusinessException(404, "PROJECT_CASE_NOT_FOUND", "项目案例不存在");
        return projectCase;
    }

    public record ProjectCaseCommand(String siteName, List<Long> imageFileIds) { }
    public record ProjectCasePage(List<ProjectCaseSummaryView> list, long total, int page, int pageSize) { }
    public record ProjectCaseSummaryView(long id, String siteName, FileView coverImage, LocalDateTime updatedAt) { }
    public record ProjectCaseDetailView(long id, String siteName, List<FileView> images,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record FileView(long id, String url) { }
}
