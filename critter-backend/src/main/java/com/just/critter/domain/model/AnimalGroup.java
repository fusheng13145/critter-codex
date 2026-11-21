package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.just.critter.domain.enums.GroupStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 群体档案，对应 animal_group 表。规模只存区间与计数依据；
 * point_ids 是 JSON 数组，读写走 JacksonTypeHandler。
 */
@TableName(value = "animal_group", autoResultMap = true)
public class AnimalGroup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupCode;
    private Long speciesId;
    private String features;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> pointIds;
    @TableField("size_min")
    private Integer minSize;
    @TableField("size_max")
    private Integer maxSize;
    private String sizeBasis;
    private GroupStatus status;
    private LocalDateTime lastSeenAt;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> mergedFrom;
    private String splitFrom;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public Long getSpeciesId() {
        return speciesId;
    }

    public void setSpeciesId(Long speciesId) {
        this.speciesId = speciesId;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    public List<Long> getPointIds() {
        return pointIds;
    }

    public void setPointIds(List<Long> pointIds) {
        this.pointIds = pointIds;
    }

    public Integer getMinSize() {
        return minSize;
    }

    public void setMinSize(Integer minSize) {
        this.minSize = minSize;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    public String getSizeBasis() {
        return sizeBasis;
    }

    public void setSizeBasis(String sizeBasis) {
        this.sizeBasis = sizeBasis;
    }

    public GroupStatus getStatus() {
        return status;
    }

    public void setStatus(GroupStatus status) {
        this.status = status;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public List<String> getMergedFrom() {
        return mergedFrom;
    }

    public void setMergedFrom(List<String> mergedFrom) {
        this.mergedFrom = mergedFrom;
    }

    public String getSplitFrom() {
        return splitFrom;
    }

    public void setSplitFrom(String splitFrom) {
        this.splitFrom = splitFrom;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}
