-- ========================================================
-- 社区居家养老服务与智能派单系统 - 基础演示数据
-- ========================================================

USE `aged_care_db`;

-- 1. 用户初始数据 (默认统一密码: 123456)
-- BCrypt: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi
INSERT INTO `sys_user` (`id`, `username`, `password`, `real_name`, `phone`, `avatar`, `role`, `openid`, `status`) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', '13800000000', '', 'ADMIN', NULL, 1),
(2, 'staff01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张玉兰', '13800000001', '', 'STAFF', NULL, 1),
(3, 'staff02', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李建国', '13800000002', '', 'STAFF', NULL, 1),
(4, 'staff03', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '王秀英', '13800000003', '', 'STAFF', NULL, 1),
(5, 'resident01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵德柱', '13800000004', '', 'RESIDENT', NULL, 1),
(6, 'family01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '赵志强', '13800000005', '', 'RESIDENT', NULL, 1),
(7, 'family02', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '孙小美', '13800000006', '', 'RESIDENT', NULL, 1);

-- 2. 老人档案
INSERT INTO `elder_profile` (`id`, `user_id`, `name`, `gender`, `id_card`, `age`, `phone`, `address`, `community`, `emergency_contact`, `emergency_phone`, `health_note`, `care_level`, `status`) VALUES
(101, 5, '赵德柱', 'MALE', '110101194205011234', 82, '13800000004', '阳光花园社区3号楼2单元301室', '阳光花园社区', '赵志强', '13800000005', '轻微高血压，生活大部分自理，腿脚偶有酸痛', 'NORMAL', 1),
(102, NULL, '钱桂芳', 'FEMALE', '110101194611152345', 78, '13800000007', '阳光花园社区5号楼1单元102室', '阳光花园社区', '孙小美', '13800000006', '轻度白内障，需要定期助洁和送餐服务', 'MEDIUM', 1),
(103, NULL, '孙大爷', 'MALE', '110101193908203456', 85, '13800000008', '阳光花园社区8号楼3单元502室', '阳光花园社区', '孙小美', '13800000006', '高龄独居老人，行动较慢，需代买日常物资', 'MEDIUM', 1);

-- 3. 家属绑定
INSERT INTO `family_binding` (`id`, `family_user_id`, `elder_id`, `relation`, `proof_materials`, `status`, `reviewer_id`, `review_time`, `review_reason`) VALUES
(201, 6, 101, '父子', '户口本亲属关系页照片', 'APPROVED', 1, NOW(), '资料齐全，核验通过'),
(202, 7, 102, '母女', '身份证及户籍关系证明', 'APPROVED', 1, NOW(), '资料属实，核验通过'),
(203, 7, 103, '叔侄', '亲属关系承诺证明', 'PENDING', NULL, NULL, '');

-- 4. 服务项目
INSERT INTO `service_item` (`id`, `name`, `category`, `description`, `price`, `duration_minutes`, `required_skills`, `service_area`, `status`, `icon`, `sort_order`) VALUES
(301, '助餐送餐', 'MEAL', '为社区居家老人提供营养保温套餐配送到家，协助摆桌并确认老人用餐。', 15.00, 30, 'COOKING', '阳光花园社区', 'ON_SHELF', 'food', 1),
(302, '居家保洁', 'CLEANING', '针对老人居住空间开展地面清扫、厨房台面擦拭、卫浴除垢及居室整理。', 45.00, 60, 'CLEANING', '阳光花园社区', 'ON_SHELF', 'cleaning', 2),
(303, '代买代办', 'ERRAND', '代购米面粮油、常用生活用品、蔬菜药品，或代缴水电燃气费。', 20.00, 45, 'ERRAND', '阳光花园社区', 'ON_SHELF', 'shopping', 3),
(304, '陪同办事就医', 'ESCORT', '全程陪同老人前往医院挂号取药或前往社区政务大厅办理老年优待卡等。', 60.00, 120, 'ESCORT', '阳光花园社区', 'ON_SHELF', 'guide', 4);

-- 5. 服务人员档案
INSERT INTO `staff_profile` (`id`, `user_id`, `service_area`, `base_station_name`, `base_longitude`, `base_latitude`, `current_status`, `audit_status`, `audit_time`, `audit_reason`, `prior_rating`, `total_orders`) VALUES
(401, 2, '阳光花园社区', '阳光社区居家养老服务站', 116.407396, 39.904199, 'AVAILABLE', 'APPROVED', NOW(), '实名资格审核通过', 4.80, 16),
(402, 3, '阳光花园社区', '阳光社区居家养老服务站', 116.407396, 39.904199, 'AVAILABLE', 'APPROVED', NOW(), '实名资格审核通过', 4.60, 9),
(403, 4, '阳光花园社区', '阳光社区居家养老服务站', 116.407396, 39.904199, 'AVAILABLE', 'APPROVED', NOW(), '实名资格审核通过', 4.90, 25);

-- 6. 服务人员技能
INSERT INTO `staff_skill` (`id`, `staff_id`, `skill_code`, `skill_name`, `certificate_no`, `audit_status`) VALUES
(501, 401, 'CLEANING', '居家保洁技能', 'CLEAN-2024-001', 'APPROVED'),
(502, 401, 'COOKING', '助餐烹饪技能', 'COOK-2024-008', 'APPROVED'),
(503, 402, 'ERRAND', '跑腿代办技能', 'ERRAND-2024-002', 'APPROVED'),
(504, 402, 'ESCORT', '助行陪办技能', 'ESCORT-2024-005', 'APPROVED'),
(505, 402, 'CLEANING', '居家保洁技能', 'CLEAN-2024-012', 'APPROVED'),
(506, 403, 'COOKING', '助餐烹饪技能', 'COOK-2024-015', 'APPROVED'),
(507, 403, 'ESCORT', '助行陪办技能', 'ESCORT-2024-009', 'APPROVED'),
(508, 403, 'NURSING', '基础护理照护', 'NURSE-2024-003', 'APPROVED');

-- 7. 服务人员排班数据 (排定今天及后续数日班次)
INSERT INTO `staff_shift` (`id`, `staff_id`, `shift_date`, `start_time`, `end_time`, `status`) VALUES
(601, 401, CURDATE(), '08:00:00', '18:00:00', 'ACTIVE'),
(602, 402, CURDATE(), '08:00:00', '18:00:00', 'ACTIVE'),
(603, 403, CURDATE(), '08:00:00', '18:00:00', 'ACTIVE'),
(604, 401, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '08:00:00', '18:00:00', 'ACTIVE'),
(605, 402, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '08:00:00', '18:00:00', 'ACTIVE'),
(606, 403, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '08:00:00', '18:00:00', 'ACTIVE'),
(607, 401, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '08:00:00', '18:00:00', 'ACTIVE'),
(608, 402, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '08:00:00', '18:00:00', 'ACTIVE'),
(609, 403, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '08:00:00', '18:00:00', 'ACTIVE');
