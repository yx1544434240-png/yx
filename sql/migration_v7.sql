-- 工作人员入驻申请；只新增表，不修改既有账号、订单或人员档案。
CREATE TABLE IF NOT EXISTS `staff_application` (
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
