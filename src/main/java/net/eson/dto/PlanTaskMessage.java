package net.eson.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author Eson
 * @date 2025年07月07日 15:47
 */
@Data
public class PlanTaskMessage implements Serializable {

    private String taskId;                     // 唯一任务 ID

    // 城市相关
    private String destinationType;            // 'specific' / 'recommend'
    private boolean isMultiCityMode;
    private List<String> destinations;         // 多城市
    private String destination;                // 单城市

    // 时间相关
    private boolean needExactDate;
    private String arrivalDateTime;
    private String arrivalLocation;
    private String departureDateTime;
    private String departureLocation;

    private Integer days;                      // 旅行天数
    private String selectedSeason;             // 季节（spring / summer / autumn / winter）

    // 推荐参数
    private List<String> selectedDestinationTypes; // 推荐时的偏好类型
    private String selectedBudget;                 // 推荐时的预算
    private List<String> preferences;              // 用户旅行偏好
}
    