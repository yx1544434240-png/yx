package com.agedcare.modules.upload.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.api.ResultCode;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.exception.BusinessException;
import com.agedcare.common.security.LoginUser;
import com.agedcare.common.security.UserContext;
import com.agedcare.common.storage.CosService;
import com.agedcare.common.storage.PrivateMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "通用文件与图片上传")
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final CosService cosService;
    private final PrivateMediaService privateMedia;
    private final com.agedcare.modules.serviceitem.service.ServiceImageStorage serviceImages;

    @Operation(summary = "上传图片到腾讯云COS对象存储")
    @PostMapping("/image")
    public Result<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "general") String type,
            @RequestParam(value = "orderId", required = false) Long orderId
    ) {
        LoginUser currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        String role = currentUser.getRole();
        String subDir;
        switch (type.toLowerCase()) {
            case "avatar":
                // 居民、服务人员、管理员均允许上传个人头像
                subDir = "avatars";
                break;
            case "fulfillment":
            case "record":
                // 仅服务人员及代办管理员允许上传履约凭证照片
                if (!UserRole.STAFF.getCode().equalsIgnoreCase(role)) {
                    throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有服务人员才能上传现场服务履约照片凭证");
                }
                return Result.success(Map.of("url", privateMedia.upload(file, orderId)));
            case "service":
                // 仅管理员允许上传养老服务项目展示图
                if (!UserRole.ADMIN.getCode().equalsIgnoreCase(role)) {
                    throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有管理员才能上传服务项目展示封面图");
                }
                return Result.success(Map.of("url", serviceImages.store(file)));
            default:
                throw new BusinessException(400, "请明确指定avatar、fulfillment或service图片用途");
        }

        String url = cosService.uploadImage(file, subDir);
        return Result.success(Map.of("url", url));
    }
}
