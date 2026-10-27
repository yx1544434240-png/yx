package com.agedcare.modules.dispatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateEvaluationResult {
    private Long staffId;
    private String staffName;
    private boolean qualified;
    private boolean locationUnknown;
    private String excludeReason;
    private int travelMinutes;
    private BigDecimal metricD;
    private BigDecimal metricQ;
    private BigDecimal metricL;
    private BigDecimal metricF;
    private BigDecimal totalScore;
    private String detailsJson;
}
