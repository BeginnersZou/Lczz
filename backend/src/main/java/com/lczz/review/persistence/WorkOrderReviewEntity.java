package com.lczz.review.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("work_order_review")
public class WorkOrderReviewEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long reviewerUserId;
    private Integer score;
    private Boolean liked;
    private String content;
    private String labelsJson;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(Long reviewerUserId) { this.reviewerUserId = reviewerUserId; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Boolean getLiked() { return liked; }
    public void setLiked(Boolean liked) { this.liked = liked; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getLabelsJson() { return labelsJson; }
    public void setLabelsJson(String labelsJson) { this.labelsJson = labelsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
