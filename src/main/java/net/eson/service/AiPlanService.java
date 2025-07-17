package net.eson.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.eson.config.RabbitMQConfig;
import net.eson.dto.PlanRequest;
import net.eson.dto.PlanResponse;
import net.eson.dto.PlanTaskMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
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
    ReactiveRedisTemplate<String,String> reactiveRedisTemplate;

    @Autowired
    ObjectMapper objectMapper;

    private static final String TASK_PREFIX = "task:";

    public String enqueueTask(PlanRequest request) {
        String taskId = UUID.randomUUID().toString();

        PlanTaskMessage msg = new PlanTaskMessage();
        msg.setDestinationType(request.getDestinationType());
        msg.setDestinations(request.getDestinations());
        msg.setMultiCityMode(request.isMultiCityMode());
        msg.setDestination(request.getDestination());
        msg.setNeedExactDate(request.isNeedExactDate());
        msg.setArrivalDateTime(request.getArrivalDateTime());
        msg.setArrivalLocation(request.getArrivalLocation());
        msg.setDepartureDateTime(request.getDepartureDateTime());
        msg.setDepartureLocation(request.getDepartureLocation());
        msg.setSelectedSeason(request.getSelectedSeason());
        msg.setDays(request.getDays());
        msg.setSelectedSeason(request.getSelectedSeason());
        msg.setSelectedDestinationTypes(request.getSelectedDestinationTypes());
        msg.setSelectedBudget(request.getSelectedBudget());
        msg.setPreferences(request.getPreferences());
        msg.setTaskId(taskId);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                msg);

        // 存空结果代表处理中（可选）
        reactiveRedisTemplate.opsForValue().set(TASK_PREFIX + taskId, "", Duration.ofMinutes(10)).subscribe();
        return taskId;
    }

    public Mono<PlanResponse> getPlanResult(String taskId) throws JsonProcessingException {
        return reactiveRedisTemplate.opsForValue()
                .get("task:" + taskId)
                .flatMap(json -> {
                    if (json == null || json.isEmpty()) {
                        return Mono.empty(); // 表示没数据或处理中
                    }
                    try {
                        PlanResponse res = objectMapper.readValue(json, PlanResponse.class);
                        return Mono.just(res);
                    } catch (JsonProcessingException e) {
                        return Mono.error(e);
                    }
                });
    }

}
    