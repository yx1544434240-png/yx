package com.agedcare.modules.staff.mapper;

import com.agedcare.modules.staff.entity.StaffProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface StaffProfileMapper extends BaseMapper<StaffProfile> {

    @Select("SELECT * FROM staff_profile WHERE id = #{id} FOR UPDATE")
    StaffProfile selectByIdForUpdate(@Param("id") Long id);
}
