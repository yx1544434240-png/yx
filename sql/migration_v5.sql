-- migration_v5.sql: 订单支付——老人本人或已审核绑定的家属均可支付，记录实际支付人，每单仅支付一次
-- 原表只有 requester_user_id(下单人)，无法区分“谁实际付了钱”，也无法判断是否已支付，因此必须新增字段。

ALTER TABLE `service_order`
    ADD COLUMN `paid_by_user_id` BIGINT DEFAULT NULL COMMENT '实际支付人ID(老人本人或已审核绑定家属)' AFTER `confirmed_at`,
    ADD COLUMN `paid_at` DATETIME DEFAULT NULL COMMENT '支付时间，非空即已支付' AFTER `paid_by_user_id`;
