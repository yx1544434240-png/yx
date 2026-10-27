package com.agedcare.modules.elder.dto;

import com.agedcare.modules.elder.entity.ElderProfile;
import lombok.Data;

import java.math.BigDecimal;

/** 管理后台老人列表：身份证号只返回脱敏值，明文不下发到浏览器。 */
@Data
public class ElderAdminVO {
    private Long id;
    private Long userId;
    private String name;
    private String gender;
    private String idCardMasked;
    private Integer age;
    private String phone;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String community;
    private String emergencyContact;
    private String emergencyPhone;
    private String healthNote;
    private String careLevel;
    private Integer status;

    public static ElderAdminVO from(ElderProfile p) {
        if (p == null) return null;
        ElderAdminVO vo = new ElderAdminVO();
        vo.setId(p.getId());
        vo.setUserId(p.getUserId());
        vo.setName(p.getName());
        vo.setGender(p.getGender());
        vo.setIdCardMasked(maskIdCard(p.getIdCard()));
        vo.setAge(p.getAge());
        vo.setPhone(p.getPhone());
        vo.setAddress(p.getAddress());
        vo.setLongitude(p.getLongitude());
        vo.setLatitude(p.getLatitude());
        vo.setCommunity(p.getCommunity());
        vo.setEmergencyContact(p.getEmergencyContact());
        vo.setEmergencyPhone(p.getEmergencyPhone());
        vo.setHealthNote(p.getHealthNote());
        vo.setCareLevel(p.getCareLevel());
        vo.setStatus(p.getStatus());
        return vo;
    }

    /** 保留前 3 位与后 4 位，例如 110***********2345；过短或为空时不返回原文。 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.isBlank()) return "";
        String value = idCard.trim();
        if (value.length() < 8) return "*".repeat(value.length());
        return value.substring(0, 3) + "*".repeat(value.length() - 7) + value.substring(value.length() - 4);
    }
}
