package net.eson.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.eson.config.RabbitMQConfig;
import net.eson.dto.PlanRequest;
import net.eson.dto.PlanResponse;
import net.eson.dto.PlanTaskMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Eson
 * @date 2025年07月07日 14:53
 */
@Service
public class AiPlanService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    private static final String TASK_PREFIX = "task:";

    public String enqueueTask(PlanRequest request) {
        String taskId = UUID.randomUUID().toString();

        PlanTaskMessage msg = new PlanTaskMessage();
        msg.setDestinations(request.getDestinations());
        msg.setMultiCityMode(request.isMultiCityMode());
        msg.setDestination(request.getDestination());
        msg.setNeedExactDate(request.isNeedExactDate());
        msg.setSelectedSeason(request.getSelectedSeason());
        msg.setDays(request.getDays());
        msg.setPreferences(request.getPreferences());
        msg.setTaskId(taskId);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                msg);

        // 存空结果代表处理中（可选）
        redisTemplate.opsForValue().set(TASK_PREFIX + taskId, "", 10, TimeUnit.MINUTES);
        return taskId;
    }

    public String getPlanResult(String taskId) throws JsonProcessingException {
        String json = (String) redisTemplate.opsForValue().get("task:" + taskId);
        if (json == null || json.isEmpty()) {
            return null; // 处理中
        }
        PlanResponse res = objectMapper.readValue(json, PlanResponse.class);
        return res.toString();
    }

}
    