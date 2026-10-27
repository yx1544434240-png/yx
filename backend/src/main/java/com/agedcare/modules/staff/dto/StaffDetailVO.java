package com.agedcare.modules.staff.dto;

import com.agedcare.modules.staff.entity.StaffProfile;
import com.agedcare.modules.staff.entity.StaffShift;
import com.agedcare.modules.staff.entity.StaffSkill;
import com.agedcare.modules.user.dto.UserSummaryVO;
import lombok.Data;

import java.util.List;

@Data
public class StaffDetailVO {
    private StaffProfile profile;
    private UserSummaryVO user;
    private List<StaffSkill> skills;
    private List<StaffShift> shifts;
}
