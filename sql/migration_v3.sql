-- migration_v3.sql: 引入地图选点与配送员实时定位轨迹追踪

ALTER TABLE `elder_profile`
    ADD COLUMN `longitude` DECIMAL(10, 6) DEFAULT 116.407396 COMMENT '老人居住地址经度' AFTER `address`,
    ADD COLUMN `latitude` DECIMAL(10, 6) DEFAULT 39.904199 COMMENT '老人居住地址纬度' AFTER `longitude`;

ALTER TABLE `staff_profile`
    ADD COLUMN `current_longitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '当前实时经度' AFTER `base_latitude`,
    ADD COLUMN `current_latitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '当前实时纬度' AFTER `current_longitude`,
    ADD COLUMN `location_updated_at` DATETIME DEFAULT NULL COMMENT '位置最后上报时间' AFTER `current_latitude`;

-- 初始化示范数据
UPDATE `elder_profile` SET `longitude` = 116.410500, `latitude` = 39.907800;
UPDATE `staff_profile` SET `current_longitude` = 116.403500, `current_latitude` = 39.901500, `location_updated_at` = NOW();
