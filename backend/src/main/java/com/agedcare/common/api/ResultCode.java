package com.agedcare.common.api;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数不合法"),
    UNAUTHORIZED(401, "尚未登录或登录凭证已失效"),
    FORBIDDEN(403, "没有该操作的访问权限"),
    NOT_FOUND(404, "请求资源未找到"),
    CONFLICT(409, "业务冲突或状态不满足流转条件"),
    INTERNAL_SERVER_ERROR(500, "系统服务内部异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
