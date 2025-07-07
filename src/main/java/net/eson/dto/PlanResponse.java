package net.eson.dto;

import lombok.Data;

import java.util.List;

/**
 * @author Eson
 * @date 2025年07月07日 17:10
 */
@Data
public class PlanResponse {

    private String taskId; // 用于前端轮询结果
    private String destination; // 单城市
    private List<String> destinations; // 多城市
    private int days;
    private List<String> preferences;
    private String planText; // AI返回的原始文本（未分段）
    private List<DayPlan> dayPlans; // 可选：结构化每一天内容

    @Data
    public static class DayPlan {
        private int day;
        private String content;
    }
}
    