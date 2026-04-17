package com.quanxiaoha.ai.robot.dto;

import lombok.Data;

@Data
public class ResumeOptimizeRequest {
    private String resumeText;
    private String targetPosition;
    private String additionalRequirements;
}
