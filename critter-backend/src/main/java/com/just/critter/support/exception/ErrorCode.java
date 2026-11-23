package com.just.critter.support.exception;

import java.util.Arrays;
import java.util.Optional;

/**
 * 错误码表，与《接口设计说明书》§3 一一对应。前端按码段做统一处理：
 * 301xx 跳登录、302xx/303xx 标红表单、304xx—306xx 弹窗说明、401xx 提示资金规则。
 */
public enum ErrorCode {

    SUCCESS(0, "ok"),

    NOT_LOGGED_IN(20001, "未登录或登录已过期"),
    NO_PERMISSION(20002, "无权限执行该操作"),
    ROLE_MISMATCH(20003, "当前角色不可执行该操作"),
    FIELD_INVISIBLE(20004, "无权查看该字段"),

    BAD_CREDENTIALS(30101, "账号或密码错误"),
    TOKEN_INVALID(30102, "登录状态已失效，请重新登录"),
    ACCOUNT_LOCKED(30103, "账号已锁定，请稍后重试"),
    ACCOUNT_DISABLED(30104, "账号已被禁用"),
    VERIFY_CODE_INVALID(30105, "验证码错误或已过期"),

    STUDENT_NO_REGISTERED(30201, "该学号已注册"),
    STUDENT_NO_NOT_IN_ROSTER(30202, "该学号不在校内名单"),

    PARAM_INVALID(30301, "参数校验失败"),
    FILE_INVALID(30302, "文件校验失败，请检查类型与大小"),
    ENUM_INVALID(30303, "枚举值非法"),
    FORMAT_INVALID(30304, "格式错误"),

    CHANGE_REASON_REQUIRED(30401, "请填写变更原因"),
    STATE_TRANSITION_INVALID(30402, "当前状态不允许该操作"),
    ACTIVE_RELATION_EXISTS(30403, "存在生效关系，无法执行该操作"),
    ANIMAL_ALREADY_ADOPTED(30404, "该动物已被认养"),
    ADOPTION_LIMIT_EXCEEDED(30405, "已超出认养上限"),
    ANIMAL_NOT_ADOPTABLE(30406, "该动物当前状态不可认养"),
    AUDIT_CONFLICT(30407, "该申请已被他人处理"),
    PAYMENT_MISMATCH(30408, "缴费金额与方案不符"),
    ADOPTION_MATERIAL_MISSING(30409, "领养材料不齐"),
    RECORD_NOT_FOUND(30410, "记录不存在"),
    CODE_DUPLICATED(30411, "编号重复"),
    PAYMENT_UNVERIFIED(30412, "未入账不得生效"),
    POINT_REFERENCED(30413, "点位已被引用，只能停用"),

    GROUP_NOT_ADOPTABLE(30501, "群体不能参与认养"),
    GROUP_CANNOT_BUILD_ANIMAL(30502, "群体不可建个体档案"),
    WILDLIFE_FORBIDDEN(30503, "大型野生动物请勿自行处置，请联系后勤"),
    SIZE_RANGE_INVALID(30504, "规模区间非法"),
    GROUP_STATE_INVALID(30505, "群体状态非法"),

    SURGERY_PHOTO_MISSING(30601, "缺少术前照片"),
    ORDER_STATE_INVALID(30602, "工单状态非法"),
    TNR_RECORD_MISSING(30603, "缺少绝育记录"),

    BUDGET_EXCEEDS_TARGET(40101, "预算合计超过目标金额"),
    DONATION_AMOUNT_INVALID(40102, "捐赠金额非法"),
    BALANCE_NOT_ENOUGH(40103, "项目余额不足"),
    SELF_REVIEW_NOT_ALLOWED(40104, "复核人不得为登记人"),
    UNREVIEWED_EXPENSE_EXISTS(40105, "存在未复核支出，无法结项"),
    DONATION_NOT_VERIFIED(40106, "未入账不得计入进度"),
    FUND_RECORD_NOT_DELETABLE(40107, "资金记录不可删除"),
    PROJECT_STATE_INVALID(40108, "项目状态非法"),

    SYSTEM_ERROR(50001, "系统繁忙，请稍后重试"),
    CONCURRENT_CONFLICT(50002, "数据已被他人修改，请刷新后重试"),
    FILE_STORAGE_ERROR(50003, "文件存储异常，请稍后重试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static Optional<ErrorCode> fromCode(int code) {
        return Arrays.stream(values()).filter(item -> item.code == code).findFirst();
    }
}
