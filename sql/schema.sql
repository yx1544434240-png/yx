-- ========================================================
-- 社区居家养老服务与智能派单系统 - 数据库结构脚本
-- MySQL 8.0+
-- 字符集: utf8mb4 / 排序规则: utf8mb4_unicode_ci
-- ========================================================

CREATE DATABASE IF NOT EXISTS `aged_care_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `aged_care_db`;

SET FOREIGN_KEY_CHECKS = 0;

-- 1. 用户账号表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `username` VARCHAR(64) NOT NULL COMMENT '登录用户名',
    `password` VARCHAR(128) NOT NULL COMMENT 'BCrypt加密密码',
    `real_name` VARCHAR(64) NOT NULL COMMENT '真实姓名',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(255) DEFAULT '' COMMENT '头像地址',
    `role` VARCHAR(32) NOT NULL COMMENT '系统主身份: ADMIN-管理员, STAFF-服务人员, RESIDENT-社区居民',
    `openid` VARCHAR(64) DEFAULT NULL COMMENT '微信小程序OpenID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常, 0-禁用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号表';

-- 2. 老人档案表
DROP TABLE IF EXISTS `elder_profile`;
CREATE TABLE `elder_profile` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '老人本人登录账号ID(可为空，家属代管)',
    `name` VARCHAR(64) NOT NULL COMMENT '老人姓名',
    `gender` VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN' COMMENT '性别: MALE, FEMALE, UNKNOWN',
    `id_card` VARCHAR(32) DEFAULT '' COMMENT '身份证号',
    `age` INT DEFAULT NULL COMMENT '年龄',
    `phone` VARCHAR(20) DEFAULT '' COMMENT '联系电话',
    `address` VARCHAR(255) NOT NULL COMMENT '详细居住地址',
    `longitude` DECIMAL(10, 6) NOT NULL DEFAULT 116.407396 COMMENT '居住地址经度',
    `latitude` DECIMAL(10, 6) NOT NULL DEFAULT 39.904199 COMMENT '居住地址纬度',
    `community` VARCHAR(100) NOT NULL DEFAULT '阳光花园社区' COMMENT '所属社区',
    `emergency_contact` VARCHAR(64) NOT NULL COMMENT '紧急联系人姓名',
    `emergency_phone` VARCHAR(20) NOT NULL COMMENT '紧急联系人电话',
    `health_note` TEXT COMMENT '健康状况与特殊需求说明',
    `care_level` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT '照护等级: NORMAL-自理, MEDIUM-介助, HIGH-介护',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-正常, 0-注销',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_elder_user` (`user_id`),
    KEY `idx_elder_community` (`community`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='老人档案表';

-- 3. 家属绑定关系表
DROP TABLE IF EXISTS `family_binding`;
CREATE TABLE `family_binding` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `family_user_id` BIGINT NOT NULL COMMENT '家属用户ID(sys_user)',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID(elder_profile)',
    `relation` VARCHAR(32) NOT NULL COMMENT '与老人关系(如: 子女, 配偶, 亲属等)',
    `proof_materials` TEXT COMMENT '证明材料或说明',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '绑定状态: PENDING-待审核, APPROVED-已通过, REJECTED-已驳回, REVOKED-已撤销',
    `reviewer_id` BIGINT DEFAULT NULL COMMENT '审核管理员ID',
    `review_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    `review_reason` VARCHAR(255) DEFAULT '' COMMENT '审核驳回或撤销原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_family_elder` (`family_user_id`, `elder_id`),
    KEY `idx_binding_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家属绑定关系表';

-- 4. 服务项目表
DROP TABLE IF EXISTS `service_item`;
CREATE TABLE `service_item` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `name` VARCHAR(64) NOT NULL COMMENT '服务项目名称',
    `category` VARCHAR(32) NOT NULL COMMENT '类别: MEAL(助餐送餐), CLEANING(居家保洁), ERRAND(代买代办), ESCORT(陪同办事)',
    `description` TEXT COMMENT '服务内容说明',
    `price` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '服务基准单价(元)',
    `duration_minutes` INT NOT NULL DEFAULT 60 COMMENT '预计服务时长(分钟)',
    `required_skills` VARCHAR(255) DEFAULT '' COMMENT '所需技能码集合(逗号分隔，如: CLEANING,COOKING)',
    `service_area` VARCHAR(100) NOT NULL DEFAULT '阳光花园社区' COMMENT '支持服务区域',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ON_SHELF' COMMENT '状态: ON_SHELF-上架, OFF_SHELF-下架',
    `icon` VARCHAR(255) DEFAULT '' COMMENT '图标或展示图',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_item_category` (`category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务项目表';

-- 5. 服务人员资料表
DROP TABLE IF EXISTS `staff_profile`;
CREATE TABLE `staff_profile` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '关联sys_user用户ID',
    `service_area` VARCHAR(100) NOT NULL DEFAULT '阳光花园社区' COMMENT '所属常驻服务区域',
    `base_station_name` VARCHAR(100) NOT NULL DEFAULT '阳光社区居家养老服务站' COMMENT '出发站点名称',
    `base_longitude` DECIMAL(10, 6) NOT NULL DEFAULT 116.407396 COMMENT '站点经度',
    `base_latitude` DECIMAL(10, 6) NOT NULL DEFAULT 39.904199 COMMENT '站点纬度',
    `current_longitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '当前实时经度',
    `current_latitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '当前实时纬度',
    `location_updated_at` DATETIME DEFAULT NULL COMMENT '位置最后上报时间',
    `current_status` VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '工作状态: RESTING-休息中, AVAILABLE-接单中, BUSY-服务中',
    `audit_status` VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态: PENDING-待审, APPROVED-通过, REJECTED-未通过',
    `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    `audit_reason` VARCHAR(255) DEFAULT '' COMMENT '审核备注',
    `prior_rating` DECIMAL(3, 2) NOT NULL DEFAULT 4.00 COMMENT '平滑历史服务均分',
    `total_orders` INT NOT NULL DEFAULT 0 COMMENT '累计完成单数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_staff_user` (`user_id`),
    KEY `idx_staff_status` (`audit_status`, `current_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务人员资料表';

-- 6. 服务人员技能表
DROP TABLE IF EXISTS `staff_skill`;
CREATE TABLE `staff_skill` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `staff_id` BIGINT NOT NULL COMMENT '服务人员ID(staff_profile)',
    `skill_code` VARCHAR(32) NOT NULL COMMENT '技能代码(如: CLEANING, COOKING, ESCORT, NURSING)',
    `skill_name` VARCHAR(64) NOT NULL COMMENT '技能名称',
    `certificate_no` VARCHAR(64) DEFAULT '' COMMENT '相关证书编号',
    `proof_urls_json` TEXT DEFAULT NULL COMMENT '私有资质证明图片列表',
    `audit_reason` VARCHAR(200) DEFAULT NULL COMMENT '审核意见或退回原因',
    `audit_user_id` BIGINT DEFAULT NULL COMMENT '审核管理员',
    `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    `submitted_at` DATETIME DEFAULT NULL COMMENT '工作人员提交时间',
    `revision` INT NOT NULL DEFAULT 0 COMMENT '申请材料版本，防止审核旧材料',
    `audit_status` VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态: PENDING, APPROVED, REJECTED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_staff_skill` (`staff_id`, `skill_code`),
    KEY `idx_skill_code` (`skill_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务人员技能表';

-- 7. 服务人员排班表
DROP TABLE IF EXISTS `staff_shift`;
CREATE TABLE `staff_shift` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `staff_id` BIGINT NOT NULL COMMENT '服务人员ID(staff_profile)',
    `shift_date` DATE NOT NULL COMMENT '排班工作日期',
    `start_time` TIME NOT NULL COMMENT '工作起始时间(如 08:30:00)',
    `end_time` TIME NOT NULL COMMENT '工作结束时间(如 17:30:00)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-有效, CANCELLED-已取消',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_staff_shift_date` (`staff_id`, `shift_date`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务人员排班表';

-- 8. 养老服务订单表
DROP TABLE IF EXISTS `service_order`;
CREATE TABLE `service_order` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '对外业务订单编号',
    `requester_user_id` BIGINT NOT NULL COMMENT '实际下单人账号ID(sys_user)',
    `requester_type` VARCHAR(20) NOT NULL DEFAULT 'SELF' COMMENT '预约人类型: SELF-老人本人自约, FAMILY-家属代约',
    `elder_id` BIGINT NOT NULL COMMENT '被服务老人档案ID(elder_profile)',
    `service_item_id` BIGINT NOT NULL COMMENT '服务项目ID(service_item)',
    `service_name_snapshot` VARCHAR(64) NOT NULL COMMENT '下单时服务名称快照',
    `service_description_snapshot` TEXT COMMENT '下单时服务说明快照',
    `required_skills_snapshot` VARCHAR(255) DEFAULT '' COMMENT '下单时技能要求快照',
    `price_snapshot` DECIMAL(10, 2) NOT NULL COMMENT '下单时服务单价快照(元)',
    `duration_minutes_snapshot` INT NOT NULL COMMENT '下单时预计时长快照(分钟)',
    `planned_start_at` DATETIME NOT NULL COMMENT '预约计划开始时间',
    `planned_end_at` DATETIME NOT NULL COMMENT '预约计划结束时间',
    `address_snapshot` VARCHAR(255) NOT NULL COMMENT '服务详细地址快照',
    `longitude` DECIMAL(10, 6) NOT NULL DEFAULT 116.407396 COMMENT '服务地址经度',
    `latitude` DECIMAL(10, 6) NOT NULL DEFAULT 39.904199 COMMENT '服务地址纬度',
    `coordinate_type` VARCHAR(20) NOT NULL DEFAULT 'GCJ02' COMMENT '坐标体系',
    `contact_name_snapshot` VARCHAR(64) NOT NULL COMMENT '联系人姓名快照',
    `contact_phone_snapshot` VARCHAR(20) NOT NULL COMMENT '联系人电话快照',
    `remark` VARCHAR(255) DEFAULT '' COMMENT '客户下单特殊备注',
    `status` VARCHAR(32) NOT NULL DEFAULT 'WAIT_DISPATCH' COMMENT '订单状态: WAIT_DISPATCH-待派单, WAIT_ACCEPT-待接单, WAIT_VISIT-待上门, IN_SERVICE-服务中, WAIT_CONFIRM-待确认, COMPLETED-已完成, WAIT_MANUAL-待人工处理, CANCELLED-已取消',
    `current_dispatch_id` BIGINT DEFAULT NULL COMMENT '当前生效的派单尝试记录ID',
    `dispatch_round` INT NOT NULL DEFAULT 1 COMMENT '调度轮次(人工重置调度后自增)',
    `idempotency_key` VARCHAR(64) DEFAULT NULL COMMENT '客户端防重复提交幂等键',
    `request_digest` VARCHAR(64) DEFAULT NULL COMMENT '请求内容摘要哈希',
    `cancel_reason` VARCHAR(255) DEFAULT '' COMMENT '取消原因',
    `cancelled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
    `confirmed_by_user_id` BIGINT DEFAULT NULL COMMENT '服务完成确认人ID',
    `confirmed_at` DATETIME DEFAULT NULL COMMENT '服务完成确认时间',
    `paid_by_user_id` BIGINT DEFAULT NULL COMMENT '实际支付人ID(老人本人或已审核绑定家属)',
    `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间，非空即已支付',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
    KEY `idx_order_requester` (`requester_user_id`, `created_at`),
    KEY `idx_order_elder` (`elder_id`, `created_at`),
    KEY `idx_order_status_time` (`status`, `planned_start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='养老服务订单表';

-- 9. 派单尝试记录表
DROP TABLE IF EXISTS `dispatch_attempt`;
CREATE TABLE `dispatch_attempt` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID(service_order)',
    `dispatch_round` INT NOT NULL DEFAULT 1 COMMENT '调度轮次',
    `attempt_no` INT NOT NULL DEFAULT 1 COMMENT '轮次内尝试序号(1~3)',
    `staff_id` BIGINT NOT NULL COMMENT '派给服务人员ID(staff_profile)',
    `dispatch_type` VARCHAR(20) NOT NULL DEFAULT 'AUTO' COMMENT '派单类型: AUTO-智能自动, MANUAL-人工指定',
    `deadline` DATETIME NOT NULL COMMENT '接单截止时间(默认发起+120秒)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING-等待接单, ACCEPTED-已接受, REJECTED-已拒绝, EXPIRED-已超时, REVOKED-已撤销',
    `accepted_at` DATETIME DEFAULT NULL COMMENT '接单时间',
    `rejected_at` DATETIME DEFAULT NULL COMMENT '拒单时间',
    `reject_reason` VARCHAR(255) DEFAULT '' COMMENT '拒单原因',
    `expired_at` DATETIME DEFAULT NULL COMMENT '超时失效时间',
    `revoked_at` DATETIME DEFAULT NULL COMMENT '撤销失效时间',
    `revoke_reason` VARCHAR(255) DEFAULT '' COMMENT '撤销原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起派单时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dispatch_staff_status` (`staff_id`, `status`),
    KEY `idx_dispatch_deadline` (`status`, `deadline`),
    KEY `idx_dispatch_order_round` (`order_id`, `dispatch_round`, `attempt_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='派单尝试记录表';

-- 10. 派单算法评分明细表
DROP TABLE IF EXISTS `dispatch_score`;
CREATE TABLE `dispatch_score` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `dispatch_round` INT NOT NULL DEFAULT 1 COMMENT '调度轮次',
    `staff_id` BIGINT NOT NULL COMMENT '候选人员ID',
    `is_qualified` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否通过硬约束: 1-合格, 0-排除',
    `exclude_reason` VARCHAR(255) DEFAULT '' COMMENT '排除原因说明(硬约束失败时填写)',
    `travel_minutes` INT NOT NULL DEFAULT 0 COMMENT '预估路程出行耗时(分钟)',
    `metric_d` DECIMAL(5, 4) NOT NULL DEFAULT 0.0000 COMMENT '到达便利度得分 D(0~1)',
    `metric_q` DECIMAL(5, 4) NOT NULL DEFAULT 0.0000 COMMENT '服务质量得分 Q(0~1)',
    `metric_l` DECIMAL(5, 4) NOT NULL DEFAULT 0.0000 COMMENT '工作量余量得分 L(0~1)',
    `metric_f` DECIMAL(5, 4) NOT NULL DEFAULT 0.0000 COMMENT '服务熟悉度得分 F(0或1)',
    `total_score` DECIMAL(6, 4) NOT NULL DEFAULT 0.0000 COMMENT '多因素加权综合总得分',
    `algorithm_version` VARCHAR(32) NOT NULL DEFAULT 'v1.0' COMMENT '算法模型版本',
    `details_json` TEXT COMMENT '打分计算上下文快照JSON',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
    PRIMARY KEY (`id`),
    KEY `idx_score_order_round` (`order_id`, `dispatch_round`, `total_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='派单算法评分明细表';

-- 11. 人员排班时间预留表
DROP TABLE IF EXISTS `staff_booking`;
CREATE TABLE `staff_booking` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `staff_id` BIGINT NOT NULL COMMENT '服务人员ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `dispatch_id` BIGINT NOT NULL COMMENT '关联派单尝试ID',
    `start_time` DATETIME NOT NULL COMMENT '预留开始时间(包含路程与服务)',
    `end_time` DATETIME NOT NULL COMMENT '预留结束时间',
    `status` VARCHAR(20) NOT NULL DEFAULT 'HELD' COMMENT '预留状态: HELD-待接单暂扣, CONFIRMED-接单锁定, RELEASED-已释放, FINISHED-履约完毕',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_booking_dispatch` (`dispatch_id`),
    KEY `idx_booking_staff_range` (`staff_id`, `start_time`, `end_time`, `status`),
    KEY `idx_booking_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员排班时间预留表';

-- 12. 订单状态流转日志表
DROP TABLE IF EXISTS `order_status_log`;
CREATE TABLE `order_status_log` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `from_status` VARCHAR(32) DEFAULT NULL COMMENT '源状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '目标状态',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `operator_role` VARCHAR(32) DEFAULT NULL COMMENT '操作人角色: SYSTEM, RESIDENT, STAFF, ADMIN',
    `reason` VARCHAR(255) DEFAULT '' COMMENT '状态变更原因或说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_log_order` (`order_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单状态流转日志表';

-- 13. 服务履约记录表
DROP TABLE IF EXISTS `service_record`;
CREATE TABLE `service_record` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `staff_id` BIGINT NOT NULL COMMENT '履约服务人员ID',
    `actual_start_at` DATETIME NOT NULL COMMENT '实际服务开始时间',
    `actual_end_at` DATETIME DEFAULT NULL COMMENT '实际服务结束时间',
    `service_summary` TEXT DEFAULT NULL COMMENT '上门服务情况与工作说明',
    `attachments_json` TEXT COMMENT '现场服务照片凭据JSON数组',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_order` (`order_id`),
    KEY `idx_record_staff` (`staff_id`, `actual_start_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务履约记录表';

-- 14. 服务评价记录表
DROP TABLE IF EXISTS `service_review`;
CREATE TABLE `service_review` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人ID',
    `reviewer_user_id` BIGINT NOT NULL COMMENT '评价人账号ID(老人或家属)',
    `rating` INT NOT NULL COMMENT '满意度评分(1~5星整数)',
    `content` TEXT COMMENT '文字评价内容',
    `tags` VARCHAR(255) DEFAULT '' COMMENT '评价标签(如: 态度好, 很准时, 细致认真)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_order` (`order_id`),
    KEY `idx_review_elder` (`elder_id`, `rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务评价记录表';

-- 15. 服务投诉与建议表
DROP TABLE IF EXISTS `service_complaint`;
CREATE TABLE `service_complaint` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `order_id` BIGINT NOT NULL COMMENT '关联订单ID',
    `complainant_id` BIGINT NOT NULL COMMENT '投诉人账号ID',
    `complaint_title` VARCHAR(128) NOT NULL COMMENT '投诉标题',
    `complaint_content` TEXT NOT NULL COMMENT '投诉具体内容',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态: PENDING-待处理, PROCESSING-处理中, RESOLVED-已办结, REJECTED-已驳回',
    `handle_user_id` BIGINT DEFAULT NULL COMMENT '处理管理员ID',
    `handle_result` TEXT COMMENT '处理答复与解决方案',
    `handled_at` DATETIME DEFAULT NULL COMMENT '办结时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_complaint_order` (`order_id`),
    KEY `idx_complaint_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务投诉与建议表';

-- 16. 站内消息通知表
DROP TABLE IF EXISTS `message_notification`;
CREATE TABLE `message_notification` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '接收人账号ID',
    `event_key` VARCHAR(64) NOT NULL COMMENT '业务事件标识(如: ORDER_DISPATCHED, ORDER_ACCEPTED)',
    `title` VARCHAR(128) NOT NULL COMMENT '通知标题',
    `content` TEXT NOT NULL COMMENT '通知详细内容',
    `is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读: 0-未读, 1-已读',
    `read_at` DATETIME DEFAULT NULL COMMENT '已读时间',
    `ref_order_id` BIGINT DEFAULT NULL COMMENT '关联业务订单ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_msg_user_read` (`user_id`, `is_read`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内消息通知表';

-- 17. 管理操作审计日志表
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
    `id` BIGINT NOT NULL COMMENT '主键ID',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人姓名',
    `action` VARCHAR(64) NOT NULL COMMENT '操作动作(如: REASSIGN_ORDER, AUDIT_STAFF)',
    `target_type` VARCHAR(64) DEFAULT NULL COMMENT '操作对象类型(如: ORDER, STAFF)',
    `target_id` BIGINT DEFAULT NULL COMMENT '操作对象ID',
    `details` TEXT COMMENT '操作具体参数及上下文快照',
    `ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_op_log_action` (`action`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理操作审计日志表';

-- 工作人员入驻申请（与技能资质审核分开）
DROP TABLE IF EXISTS `staff_application`;
CREATE TABLE `staff_application` (
    `id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `real_name` VARCHAR(32) NOT NULL,
    `phone` VARCHAR(20) NOT NULL,
    `service_area` VARCHAR(100) NOT NULL,
    `introduction` VARCHAR(500) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `revision` INT NOT NULL DEFAULT 1,
    `audit_reason` VARCHAR(200) DEFAULT NULL,
    `audit_user_id` BIGINT DEFAULT NULL,
    `audit_time` DATETIME DEFAULT NULL,
    `submitted_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL,
    `updated_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_application_user` (`user_id`),
    KEY `idx_application_status` (`status`, `submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作人员入驻申请';

SET FOREIGN_KEY_CHECKS = 1;
