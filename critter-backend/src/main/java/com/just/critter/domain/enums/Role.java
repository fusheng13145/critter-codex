package com.just.critter.domain.enums;

/**
 * 角色。主角色唯一，认养人／志愿者为叠加身份；系统管理员不具备业务审核权。
 */
public enum Role {
    STUDENT,
    ADOPTER,
    VOLUNTEER,
    ANIMAL_ADMIN,
    SYSTEM_ADMIN,
    SYSTEM
}
