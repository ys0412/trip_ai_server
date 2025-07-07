package net.eson.component;

import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import net.eson.config.RabbitMQConfig;
import net.eson.dto.PlanResponse;
import net.eson.dto.PlanTaskMessage;
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

    @Autowired
    private OpenAiService openAiService;


    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void receive(PlanTaskMessage message) {
        log.info("收到AI行程任务：{}", message);

        try {
            String resultText = generatePlanWithOpenAI(message);
            PlanResponse response = buildResponse(message.getTaskId(), message, resultText);

            redisTemplate.opsForValue().set(
                    TASK_PREFIX + message.getTaskId(),
                    response,
                    30, TimeUnit.MINUTES
            );

            log.info("任务完成：taskId = {}", message.getTaskId());
        } catch (Exception e) {
            String failMsg = "生成失败：" + e.getMessage();
            redisTemplate.opsForValue().set(
                    TASK_PREFIX + message.getTaskId(),
                    failMsg,
                    10, TimeUnit.MINUTES
            );
            log.error("调用OpenAI失败", e);
        }
    }

    private String generatePlanWithOpenAI(PlanTaskMessage msg) {
        String prompt = buildPrompt(msg);

        CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .model("text-davinci-003") // 或 gpt-3.5 模型
                .temperature(0.7)
                .maxTokens(800)
                .build();

        return openAiService.createCompletion(request)
                .getChoices()
                .get(0)
                .getText()
                .trim();
    }

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
}
    