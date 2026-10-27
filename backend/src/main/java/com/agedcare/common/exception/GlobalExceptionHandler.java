package com.agedcare.common.exception;

import com.agedcare.common.api.Result;
import com.agedcare.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<Void> handleUploadSizeException(MaxUploadSizeExceededException e) {
        return Result.error(ResultCode.BAD_REQUEST.getCode(), "单个文件不能超过5MB；服务项目图片不能超过2MB，请压缩后上传");
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::friendlyMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        log.warn("参数校验异常: {}", msg);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::friendlyMessage)
                .distinct()
                .collect(Collectors.joining("; "));
        log.warn("数据绑定异常: {}", msg);
        return Result.error(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return Result.error(ResultCode.BAD_REQUEST.getCode(), e.getMessage());
    }

    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class, org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public Result<Void> handleConstraintException(Exception e) {
        return Result.error(400, "请求参数不合法，请检查页码、数量及必填字段");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统未知异常: ", e);
        return Result.error(ResultCode.INTERNAL_SERVER_ERROR.getCode(), "系统处理繁忙，请稍后再试");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public Result<Void> handleNotFound(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return Result.error(404,"请求的资源不存在");
    }

    /** 注解未写中文提示时（如 @Size(max=255)），回退为带字段名的中文提示，避免返回英文默认文案。 */
    static String friendlyMessage(FieldError error) {
        String message = error.getDefaultMessage();
        if (message != null && message.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
            return message;
        }
        return "参数“" + error.getField() + "”填写不正确，请检查后重试";
    }
}
