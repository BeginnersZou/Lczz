package com.lczz.file.service;

import com.lczz.common.exception.BusinessException;
import java.util.List;

/** Limits are per progress record and per media type, shared by submission and later edits. */
public final class ProgressMediaLimits {
    public static final int IMAGE_LIMIT = 30;
    public static final int VIDEO_LIMIT = 9;
    public static final int TOTAL_LIMIT = IMAGE_LIMIT + VIDEO_LIMIT;

    private ProgressMediaLimits() { }

    public static void validate(List<String> mimeTypes) {
        int images = 0;
        int videos = 0;
        for (String mime : mimeTypes) {
            if (mime != null && mime.startsWith("image/")) images++;
            else if (mime != null && mime.startsWith("video/")) videos++;
            else throw new BusinessException("INVALID_PROGRESS_FILE", "施工附件仅支持图片或视频");
        }
        if (images > IMAGE_LIMIT) throw new BusinessException("FILE_COUNT_LIMIT", "每条施工进度最多30张图片");
        if (videos > VIDEO_LIMIT) throw new BusinessException("FILE_COUNT_LIMIT", "每条施工进度最多9个视频");
    }
}
