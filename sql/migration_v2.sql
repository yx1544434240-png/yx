-- 社区养老系统增量迁移脚本 V2
-- 适用于已有 aged_care_db 数据库的平滑升级

-- 1. 允许微信居民无手机号注册，避免空字符串与唯一索引冲突
ALTER TABLE `sys_user` MODIFY `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号';

-- 2. 允许进行中的服务履约记录暂存实际开始时间，结束时间与服务总结在完成时补齐
ALTER TABLE `service_record` MODIFY `actual_end_at` DATETIME DEFAULT NULL COMMENT '实际服务结束时间';
ALTER TABLE `service_record` MODIFY `service_summary` TEXT DEFAULT NULL COMMENT '上门服务情况与工作说明';
