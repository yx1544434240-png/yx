package com.agedcare.modules.order.dto;

import com.agedcare.modules.elder.entity.ElderProfile;
import lombok.Data;

import java.math.BigDecimal;

/** 订单详情/列表中的老人信息：只包含服务所需字段，不含身份证号等敏感信息。 */
@Data
public class OrderElderVO {
    private Long id;
    private String name;
    private String gender;
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

    public static OrderElderVO from(ElderProfile p) {
        if (p == null) return null;
        OrderElderVO vo = new OrderElderVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setGender(p.getGender());
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
        return vo;
    }
}
