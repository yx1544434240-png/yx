-- 为现有 staff_skill 增加申请材料与审核信息；幂等，仅新增列，不修改历史资质状态。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='staff_skill' AND column_name='proof_urls_json')=0, 'ALTER TABLE staff_skill ADD COLUMN proof_urls_json TEXT DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='staff_skill' AND column_name='audit_reason')=0, 'ALTER TABLE staff_skill ADD COLUMN audit_reason VARCHAR(200) DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='staff_skill' AND column_name='audit_user_id')=0, 'ALTER TABLE staff_skill ADD COLUMN audit_user_id BIGINT DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='staff_skill' AND column_name='audit_time')=0, 'ALTER TABLE staff_skill ADD COLUMN audit_time DATETIME DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='staff_skill' AND column_name='submitted_at')=0, 'ALTER TABLE staff_skill ADD COLUMN submitted_at DATETIME DEFAULT NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='staff_skill' AND column_name='revision')=0, 'ALTER TABLE staff_skill ADD COLUMN revision INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
