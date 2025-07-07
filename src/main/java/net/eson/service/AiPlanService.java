package net.eson.service;

import net.eson.config.RabbitMQConfig;
import net.eson.dto.PlanRequest;
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

    private static final String TASK_PREFIX = "task:";

    public String enqueueTask(PlanRequest request) {
        String taskId = UUID.randomUUID().toString();

        PlanTaskMessage msg = new PlanTaskMessage();
        msg.setTaskId(taskId);
        msg.setDays(request.getDays());
        msg.setPreferences(request.getPreferences());

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                msg);

        // 存空结果代表处理中（可选）
        redisTemplate.opsForValue().set(TASK_PREFIX + taskId, "", 10, TimeUnit.MINUTES);
        return taskId;
    }

    public String getPlanResult(String taskId) {
        Object value = redisTemplate.opsForValue().get(TASK_PREFIX + taskId);
        if (value == null || value.toString().isEmpty()) {
            return null; // 处理中
        }
        return value.toString();
    }

}
    