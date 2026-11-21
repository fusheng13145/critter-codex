package com.just.critter.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.domain.enums.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个体档案，对应 animal 表。字段与《数据库设计说明书》§3 一致；
 * deleted 逻辑删除，version 乐观锁，状态只能走状态机，不在本类里直接改。
 */
@TableName("animal")
public class Animal {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private AnimalCategory category;
    private String name;
    private Gender gender;
    private String color;
    private Integer estimatedAge;
    private String features;
    private Long pointId;
    private AnimalStatus status;
    private Boolean neutered;
    private LocalDate neuteredAt;
    private Boolean adopted;
    private String coverFileId;
    private String internalNote;
    private LocalDateTime statusChangedAt;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public AnimalCategory getCategory() {
        return category;
    }

    public void setCategory(AnimalCategory category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getEstimatedAge() {
        return estimatedAge;
    }

    public void setEstimatedAge(Integer estimatedAge) {
        this.estimatedAge = estimatedAge;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    public Long getPointId() {
        return pointId;
    }

    public void setPointId(Long pointId) {
        this.pointId = pointId;
    }

    public AnimalStatus getStatus() {
        return status;
    }

    public void setStatus(AnimalStatus status) {
        this.status = status;
    }

    public Boolean getNeutered() {
        return neutered;
    }

    public void setNeutered(Boolean neutered) {
        this.neutered = neutered;
    }

    public LocalDate getNeuteredAt() {
        return neuteredAt;
    }

    public void setNeuteredAt(LocalDate neuteredAt) {
        this.neuteredAt = neuteredAt;
    }

    public Boolean getAdopted() {
        return adopted;
    }

    public void setAdopted(Boolean adopted) {
        this.adopted = adopted;
    }

    public String getCoverFileId() {
        return coverFileId;
    }

    public void setCoverFileId(String coverFileId) {
        this.coverFileId = coverFileId;
    }

    public String getInternalNote() {
        return internalNote;
    }

    public void setInternalNote(String internalNote) {
        this.internalNote = internalNote;
    }

    public LocalDateTime getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(LocalDateTime statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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
