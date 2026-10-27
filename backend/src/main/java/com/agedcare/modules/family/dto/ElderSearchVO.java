package com.agedcare.modules.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "老人档案检索脱敏核验结果")
public class ElderSearchVO {

    @Schema(description = "老人档案ID")
    private Long id;

    @Schema(description = "脱敏姓名(如: 张*华)")
    private String name;

    @Schema(description = "脱敏联系电话(如: 138****1234)")
    private String phone;

    @Schema(description = "所属社区")
    private String community;

    @Schema(description = "是否存在有效在册档案")
    private Boolean exists;
}
