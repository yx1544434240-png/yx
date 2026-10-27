package com.agedcare.modules.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import jakarta.validation.constraints.*;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(value={"id", "createdAt", "updatedAt"}, allowGetters=true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("staff_shift")
public class StaffShift implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @NotNull @Positive
    private Long staffId;
    @NotNull
    private LocalDate shiftDate;
    @NotNull
    private LocalTime startTime;
    @NotNull
    private LocalTime endTime;
    @Pattern(regexp="ACTIVE|CANCELLED")
    private String status; // ACTIVE, CANCELLED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
