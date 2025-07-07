package net.eson.dto;

import lombok.Data;

import java.util.List;

/**
 * @author Eson
 * @date 2025年07月07日 15:18
 */
@Data
public class PlanRequest {
    private String destinationType;         // specific / recommend
    private boolean isMultiCityMode;        // 是否多城市
    private List<String> destinations;      // 城市列表（多城市模式下使用）
    private String destination;             // 单城市模式使用

    private boolean needExactDate;          // 是否启用具体时间
    private String arrivalDateTime;         // 到达时间（格式如：2025-08-01 10:30）
    private String arrivalLocation;         // 到达地点
    private String departureDateTime;       // 离开时间
    private String departureLocation;       // 离开地点

    private Integer days;                   // 出行天数（非具体时间模式下使用）
    private String selectedSeason;          // 出行季节，如 spring / summer 等

    private List<String> selectedDestinationTypes; // 推荐模式下的目的地类型偏好
    private String selectedBudget;                // 推荐模式下的预算区间
    private List<String> preferences;             // 旅行偏好
}
    