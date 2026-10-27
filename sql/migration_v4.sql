-- migration_v4.sql: 完善老人自约与家属代约全流程显性化

ALTER TABLE `service_order`
    ADD COLUMN `requester_type` VARCHAR(20) NOT NULL DEFAULT 'SELF' COMMENT '预约人类型: SELF-老人本人自约, FAMILY-家属代约' AFTER `requester_user_id`;

-- 根据历史订单数据智能回填：若老人档案 user_id 与下单人一致则为 SELF，否则为 FAMILY
UPDATE `service_order` o
JOIN `elder_profile` e ON o.elder_id = e.id
SET o.requester_type = CASE
    WHEN e.user_id = o.requester_user_id THEN 'SELF'
    ELSE 'FAMILY'
END;
