package net.eson.component;

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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

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
    private RedisTemplate<String, Object> redisTemplate;

//    @Autowired
//    private OpenAiService openAiService;

    @Autowired
    private QwenApiClient qwenApiClient;

    @Autowired
    ObjectMapper objectMapper;


    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receiveStream(PlanTaskMessage msg) {
        log.info("收到AI行程任务：{}", msg);
        String taskId = msg.getTaskId();
        String channel = "plan-delta:" + taskId;   // SSE/WebSocket 订阅的主题
        StringBuilder full = new StringBuilder();  // 累积完整文本

        try {
            // 开启流调用，返回 BufferedSource
            BufferedSource source = qwenApiClient.callQwenStream(buildPrompt(msg));

            while (!source.exhausted()) {
                String line = source.readUtf8Line();
                if (line == null) break; // 流结束
                if (line.isBlank()) continue;
                if ("[DONE]".equals(line)) break;

                // 解析 line -> deltaText（按 DashScope 规范提取 output.text）
                String delta = extractDelta(line);

                // 1️⃣ 推送增量
                redisTemplate.convertAndSend(channel, delta);

                // 2️⃣ 累积
                full.append(delta);
            }

            // 发送结束标记
            redisTemplate.convertAndSend(channel, "[DONE]");

            // 3️⃣ 构建最终 PlanResponse
            PlanResponse resp = buildResponse(taskId, msg, full.toString());
            redisTemplate.opsForValue().set(TASK_PREFIX + taskId,
                    objectMapper.writeValueAsString(resp),
                    30, TimeUnit.MINUTES);

            log.info("流式任务完成：{}", taskId);

        } catch (Exception e) {
            redisTemplate.convertAndSend(channel, "[ERROR]");
            log.error("流式生成失败", e);
        }
    }

//    private String generatePlanWithOpenAI(PlanTaskMessage msg) {
//        String prompt = buildPrompt(msg);
//
//        CompletionRequest request = CompletionRequest.builder()
//                .prompt(prompt)
//                .model("text-davinci-003") // 或 gpt-3.5 模型
//                .temperature(0.7)
//                .maxTokens(800)
//                .build();
//
//        return openAiService.createCompletion(request)
//                .getChoices()
//                .get(0)
//                .getText()
//                .trim();
//    }

    private String buildPrompt(PlanTaskMessage msg) {
        String cities = msg.isMultiCityMode() ? String.join("、", msg.getDestinations()) : msg.getDestination();
        String prefs = msg.getPreferences() != null ? String.join("、", msg.getPreferences()) : "无特别偏好";
        return String.format("帮我规划一个 %d 天的旅行计划，城市是：%s，偏好是：%s，请详细列出每天的行程。",
                msg.getDays(),
                cities,
                prefs
        );
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
    