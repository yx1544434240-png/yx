package com.agedcare.modules.user.controller;

import com.agedcare.common.api.Result;
import com.agedcare.common.enums.UserRole;
import com.agedcare.common.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@org.springframework.validation.annotation.Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/operation-logs")
@RequireRole(UserRole.ADMIN)
public class OperationLogController {
    static final String SYSTEM_TASK_FILTER = " WHERE NOT (operator_name = 'SYSTEM' AND action LIKE 'LEGACY\\_PHOTO\\_%')";
    private final JdbcTemplate jdbc;

    static final Map<String, String> ACTION_MAP = Map.ofEntries(
            Map.entry("ServiceItemController.saveOrUpdate", "保存服务项目"),
            Map.entry("ServiceItemController.uploadImage", "上传服务封面图"),
            Map.entry("ServiceItemController.toggleStatus", "上架/下架服务项目"),
            Map.entry("ServiceItemController.updateStatus", "调整服务状态"),
            Map.entry("ServiceItemController.delete", "删除服务项目"),
            Map.entry("DispatchController.assign", "人工指派调度"),
            Map.entry("DispatchController.reassign", "重新指派服务人员"),
            Map.entry("DispatchController.accept", "服务人员接单"),
            Map.entry("DispatchController.reject", "服务人员拒单"),
            Map.entry("ElderProfileController.saveOrUpdate", "维护老人健康档案"),
            Map.entry("ElderProfileController.save", "保存老人健康档案"),
            Map.entry("FamilyBindingController.apply", "提交家属代约申请"),
            Map.entry("FamilyBindingController.audit", "审核家属绑定申请"),
            Map.entry("FamilyBindingController.revoke", "解除家属绑定关系"),
            Map.entry("FulfillmentController.startService", "服务人员开始上门"),
            Map.entry("FulfillmentController.submitRecord", "提交服务核销凭证"),
            Map.entry("FulfillmentController.confirm", "确认完成服务订单"),
            Map.entry("FulfillmentController.submitReview", "提交服务评价"),
            Map.entry("FulfillmentController.submitComplaint", "提交服务投诉建议"),
            Map.entry("FulfillmentController.startComplaint", "受理服务投诉建议"),
            Map.entry("FulfillmentController.handleComplaint", "处理办结投诉建议"),
            Map.entry("OrderController.createOrder", "创建预约订单"),
            Map.entry("OrderController.create", "创建预约订单"),
            Map.entry("OrderController.cancelOrder", "取消预约订单"),
            Map.entry("OrderController.cancel", "取消预约订单"),
            Map.entry("OrderController.payOrder", "支付结算订单"),
            Map.entry("OrderController.pay", "支付结算订单"),
            Map.entry("StaffController.saveOrUpdate", "维护服务人员档案"),
            Map.entry("StaffController.audit", "审核人员执业资质"),
            Map.entry("StaffController.updateStatus", "调整人员在岗状态"),
            Map.entry("StaffController.addSkill", "认证添加专业技能"),
            Map.entry("StaffController.removeSkill", "移除人员专业技能"),
            Map.entry("StaffController.addShift", "安排服务人员排班"),
            Map.entry("StaffController.reportLocation", "上报人员实时坐标"),
            Map.entry("StaffController.mockMoveLocation", "模拟人员位置移动"),
            Map.entry("UserController.updateStatus", "启用/冻结系统账号"),
            Map.entry("UserController.updatePassword", "修改系统登录密码"),
            Map.entry("UserController.updateAvatar", "更换用户个人头像"),
            Map.entry("UserController.updateProfile", "修改用户个人资料"),
            Map.entry("FileUploadController.uploadImage", "上传系统图片文件"),
            Map.entry("NotificationController.markAsRead", "标记系统通知已读"),
            Map.entry("NotificationController.read", "标记系统通知已读"),
            Map.entry("AuthController.login", "账号密码登录"),
            Map.entry("AuthController.wechatLogin", "微信授权登录"),
            Map.entry("MockAuthController.mockLogin", "模拟快速登录"),
            Map.entry("MockAuthController.register", "创建答辩演示用户"),
            Map.entry("StaffApplicationController.submit", "提交服务人员入驻申请"),
            Map.entry("StaffApplicationController.audit", "审核服务人员入驻申请"),
            Map.entry("StaffCredentialController.submit", "提交专业资质材料"),
            Map.entry("StaffCredentialController.audit", "审核专业资质证明"),
            Map.entry("LEGACY_PHOTO_PENDING", "历史私有图片待清理"),
            Map.entry("LEGACY_PHOTO_CLEANED", "历史私有图片已清理归档")
    );

    static final Map<String, String> TARGET_TYPE_MAP = Map.ofEntries(
            Map.entry("ServiceItemController", "服务项目"),
            Map.entry("SERVICE_ITEM", "服务项目"),
            Map.entry("DispatchController", "调度派单"),
            Map.entry("DISPATCH", "调度派单"),
            Map.entry("ElderProfileController", "老人档案"),
            Map.entry("ELDER", "老人档案"),
            Map.entry("FamilyBindingController", "家属绑定"),
            Map.entry("FAMILY_BINDING", "家属绑定"),
            Map.entry("FulfillmentController", "履约服务"),
            Map.entry("FULFILLMENT", "履约服务"),
            Map.entry("OrderController", "服务订单"),
            Map.entry("ORDER", "服务订单"),
            Map.entry("StaffController", "服务人员"),
            Map.entry("STAFF", "服务人员"),
            Map.entry("UserController", "系统用户"),
            Map.entry("USER", "系统用户"),
            Map.entry("FileUploadController", "文件附件"),
            Map.entry("UPLOAD", "文件附件"),
            Map.entry("NotificationController", "消息通知"),
            Map.entry("NOTIFICATION", "消息通知"),
            Map.entry("SERVICE_RECORD", "服务核销记录"),
            Map.entry("SYSTEM", "系统任务")
    );

    public static Map<String, Object> enrichRecord(Map<String, Object> row) {
        Map<String, Object> enriched = new LinkedHashMap<>(row);
        Object action = enriched.get("action") != null ? enriched.get("action") : enriched.get("ACTION");
        if (action != null) {
            String actStr = String.valueOf(action);
            enriched.put("action_text", ACTION_MAP.getOrDefault(actStr, formatActionFallback(actStr)));
        }
        Object targetType = enriched.get("target_type") != null ? enriched.get("target_type") : enriched.get("TARGET_TYPE");
        if (targetType != null) {
            String ttStr = String.valueOf(targetType);
            enriched.put("target_type_text", TARGET_TYPE_MAP.getOrDefault(ttStr, ttStr.replace("Controller", "")));
        }
        return enriched;
    }

    private static String formatActionFallback(String action) {
        String[] parts = action.split("\\.");
        if (parts.length == 2) {
            String target = TARGET_TYPE_MAP.getOrDefault(parts[0], parts[0].replace("Controller", ""));
            return target + " - " + parts[1];
        }
        return action;
    }

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") @jakarta.validation.constraints.Min(1) int pageNum, @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(100) int pageSize,
                                                @RequestParam(defaultValue = "false") boolean includeSystem) {
        int size = Math.max(1, Math.min(100, pageSize));
        int page = Math.max(1, Math.min(100000, pageNum));
        // 默认只展示管理员操作；系统清理任务(SYSTEM + LEGACY_PHOTO_*)需 includeSystem=true 才返回
        String where = includeSystem ? "" : SYSTEM_TASK_FILTER;
        List<Map<String, Object>> records = jdbc.queryForList("SELECT * FROM operation_log" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", size, (page - 1) * size);
        List<Map<String, Object>> enriched = records.stream().map(OperationLogController::enrichRecord).toList();
        return Result.success(Map.of("records", enriched,
                "total", Objects.requireNonNullElse(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log" + where, Long.class), 0L)));
    }
}
