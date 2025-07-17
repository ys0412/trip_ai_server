package net.eson.component;

import com.alibaba.dashscope.common.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import net.eson.config.RabbitMQConfig;
import net.eson.dto.PlanResponse;
import net.eson.dto.PlanTaskMessage;
import okio.BufferedSource;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author Eson
 * @date 2025年07月07日 16:53
 */
@Slf4j
@Component
public class PlanTaskConsumer {
    private static final String TASK_PREFIX = "task:";

    @Autowired
    private ReactiveRedisTemplate<String,String> reactiveRedisTemplate;

//    @Autowired
//    private OpenAiService openAiService;

    @Autowired
    private QwenApiClient qwenApiClient;

    @Autowired
    ObjectMapper objectMapper;


    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME, containerFactory = "rabbitListenerContainerFactory")
    public void receiveStream(PlanTaskMessage msg) {
        log.info("收到AI行程任务：{}", msg);
        String taskId = msg.getTaskId();
        String channel = "plan-delta:" + taskId;   // SSE/WebSocket 订阅的主题
        StringBuilder full = new StringBuilder();  // 累积完整文本

        try {
            Message userMsg = buildPrompt(msg); // 你需要把 buildPrompt 返回 String 改为 Message 或改造

            qwenApiClient.streamCallWithMessage(userMsg, delta -> {
                // 推送增量
                reactiveRedisTemplate.convertAndSend(channel, delta).subscribe();
                // 累积
                full.append(delta);
            });

            // 发送结束标记
            reactiveRedisTemplate.convertAndSend(channel, "[DONE]").subscribe();

            // 构建最终 PlanResponse
            PlanResponse resp = buildResponse(taskId, msg, full.toString());
            reactiveRedisTemplate.opsForValue().set(TASK_PREFIX + taskId,
                    objectMapper.writeValueAsString(resp),
                     Duration.ofMinutes(30)).subscribe();

            log.info("流式任务完成：{}", taskId);

        } catch (Exception e) {
            reactiveRedisTemplate.convertAndSend(channel, "[ERROR]").subscribe();
            log.error("流式生成失败", e);
        }
    }

    public Message buildPrompt(PlanTaskMessage msg) {
        String cities = msg.isMultiCityMode()
                ? String.join("、", msg.getDestinations())
                : msg.getDestination();

        String budget = msg.getSelectedBudget() != null ? msg.getSelectedBudget() : "适中";
        String season = msg.getSelectedSeason() != null ? msg.getSelectedSeason() : "全年皆宜";
        String prefs = msg.getPreferences() != null
                ? String.join("、", msg.getPreferences())
                : "无特别偏好";

        String destTypes = msg.getSelectedDestinationTypes() != null
                ? String.join("、", msg.getSelectedDestinationTypes())
                : "不限类型";

        if ("recommend".equalsIgnoreCase(msg.getDestinationType())) {
            String promptText = String.format("""
           你是一位专业旅行规划师，擅长根据季节、预算、偏好等维度为用户量身定制旅行路线。请严格遵守以下规则：

- 必须推荐 **1~2 个真实的中国境内城市名称**
- **不得**使用“某城市”“打卡地A”“景点B”这类虚拟表达
- **必须**为推荐的第一个城市生成**完整、真实、可执行**的每日行程计划
- **禁止**向用户提问或请求补充信息
- **禁止**输出与旅行无关的内容

【用户旅行需求】
- 出行天数：%d 天
- 预算：%s
- 出行季节：%s
- 偏好目的地类型：%s（例如：自然风光、人文历史、都市购物等）
- 个人兴趣偏好：%s（例如：美食、轻松、摄影、文化等）

【输出内容结构（按以下顺序输出）】

---

## 推荐城市

- 推荐城市 1：请写出真实城市名，并说明推荐理由（如：符合预算、适合季节、满足偏好等）
- 推荐城市 2（可选）：同上

---

## 旅行主题概述（限一行）

- 用一句话总结本次旅行的核心亮点与体验氛围（30 字以内）

---

## 行程安排（每日安排）

请为“推荐城市 1”提供真实的行程安排  
每段安排需包括：
- 实际景点名称 + 推荐时间（如：颐和园（约 2 小时））
- 简要背景/亮点描述
- 建议交通方式（如：步行 / 地铁 / 打车）

格式示例：

### 第 1 天
- **上午**：灵隐寺（约 2 小时）—参拜千年古刹，感受江南佛教文化（公交至“灵隐”站）
- **下午**：西湖断桥（约 1 小时）—打卡杭州经典景点，适合拍照留念（地铁至龙翔桥站后步行）
- **晚上**：南宋御街夜市 — 品尝片儿川、小笼包、臭豆腐等杭州美食（步行游览）

（依此类推，列出每一天）

---

## 美食推荐

每日推荐 1~2 家当地餐厅 + 菜名  
示例：
- **楼外楼**：西湖醋鱼、叫花鸡  
- **知味观**：片儿川、小笼包

---

## 实用小贴士

- 写出 3 条出行建议或注意事项（天气、交通、门票等）

---

## 注意事项

- 请不要生成“某城市”、“打卡地A”等模糊信息
- 若字段为空，请使用“无特别偏好”代替
- 内容应详细完整、逻辑通顺，能直接生成 PDF 行程单
""",
                    msg.getDays(),
                    budget,
                    season,
                    destTypes,
                    prefs);

            return Message.builder()
                    .role("user")
                    .content(promptText)
                    .build();
        }

        // specific 模式
        String promptText = String.format("""
                你是一位专业旅游策划师，擅长为不同人群设计贴合季节、预算和兴趣的旅行计划。请根据以下用户需求，制定一份**完整、清晰、结构化**的旅行安排。

                【用户旅行需求】
                - 城市：%s
                - 出行天数：%d 天
                - 出行季节：%s
                - 偏好目的地类型：%s（例如：自然风光、人文历史、都市购物、山川湖泊等）
                - 兴趣偏好：%s（例如：美食、轻松、摄影、文化、亲子等）

                【输出格式要求】
                请使用 Markdown 格式分节输出，包括以下几部分内容（顺序不可变）：

                ---

                ## 旅行主题概述（限一行）
                - 用一句话（不超过 30 字）总结这次行程的主题与体验感，比如：“两天轻松漫游杭州西湖，品味江南水乡文化”

                ---

                ## 行程安排（每日安排）
                ### 第 X 天
                - **上午**：推荐景点 + 简要背景 + 停留建议（例如：“灵隐寺（约 1.5 小时）：参观千年古刹，感受佛教文化”）
                - **下午**：同上
                - **晚上**：夜景 / 夜市 / 表演 / 放松建议

                - 每个时间段还需简要描述交通方式（如：地铁/公交/步行）

                ### 第 X+1 天
                （依此类推）

                ---

                ## 美食推荐（每日推荐）
                - 每天推荐 1~2 家本地特色餐馆，附带推荐菜名  
                  示例：  
                  - **楼外楼**：西湖醋鱼、叫花鸡  
                  - **知味观**：片儿川、小笼包

                ---

                ## 实用小贴士
                - 总结 3 条出行建议或注意事项，例如：  
                  - 本地多雨，建议随身携带雨具  
                  - 景点集中，步行较多，请穿舒适鞋子  
                  - 周末景区人流较大，建议避开高峰时段

                ---

                ## 注意事项
                - 请不要输出“请问您要去哪”或“请补充信息”之类的话  
                - 无需与用户互动，直接生成最终内容  
                - 如部分字段缺失（如偏好为空），请使用“无特别偏好”代替  
                - 内容务必详尽完整，可直接用于生成 PDF 行程单
                """,
                cities,
                msg.getDays(),
                season,
                destTypes,
                prefs);

        return Message.builder()
                .role("user")
                .content(promptText)
                .build();
    }

    public PlanResponse buildResponse(String taskId, PlanTaskMessage msg, String aiResultText) {
        PlanResponse response = new PlanResponse();
        response.setTaskId(taskId);
        response.setDestination(msg.getDestination());
        response.setDestinations(msg.getDestinations());
        response.setDays(msg.getDays());
        response.setPreferences(msg.getPreferences());
        response.setPlanText(aiResultText);

        // 简单分行处理为每日计划（可优化）
        AtomicInteger counter = new AtomicInteger(1);
        List<PlanResponse.DayPlan> dayPlans = Arrays.stream(aiResultText.split("Day \\d+:"))
                .filter(line -> !line.trim().isEmpty())
                .map(text -> {
                    PlanResponse.DayPlan day = new PlanResponse.DayPlan();
                    int dayIndex = counter.getAndIncrement();
                    day.setDay(dayIndex);
                    day.setContent("Day " + dayIndex + ": " + text.trim());
                    return day;
                })
                .collect(Collectors.toList());

        response.setDayPlans(dayPlans);
        return response;
    }

    /**
     * 从 DashScope 流式返回的单行 JSON 中提取增量文本
     * 行示例：
     * {"output":{"text":"Day 1: ..."},"usage":{...},"request_id":"xxx"}
     */
    private String extractDelta(String line) {
        try {
            // 部分平台会在行首加 "data: " 前缀，先去掉
            if (line.startsWith("data:")) {
                line = line.substring(5).trim();
            }
            JsonNode root = objectMapper.readTree(line);
            // DashScope 规范：增量文本在 output.text
            return root.at("/output/text").asText("");
        } catch (Exception e) {
            // 解析异常时返回空串，避免中断流
            log.warn("无法解析 delta 行: {}", line, e);
            return "";
        }
    }
}
    