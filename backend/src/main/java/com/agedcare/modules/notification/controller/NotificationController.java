package com.agedcare.modules.notification.controller;

import com.agedcare.common.api.Result;
import com.agedcare.modules.notification.entity.MessageNotification;
import com.agedcare.modules.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "站内消息通知")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "获取当前用户所有站内通知")
    @GetMapping("/my-list")
    public Result<List<MessageNotification>> getMyList() {
        return Result.success(notificationService.getMyNotifications());
    }

    @Operation(summary = "标记通知为已读")
    @PostMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success();
    }
}
