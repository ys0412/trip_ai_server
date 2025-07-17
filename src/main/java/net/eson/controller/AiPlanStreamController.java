package net.eson.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import net.eson.dto.PlanRequest;
import net.eson.service.AiPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * @author Eson
 * @date 2025年07月11日 13:43
 */
@Slf4j
@RestController
@RequestMapping("/api/plan")
public class AiPlanStreamController {

    @Autowired
    ReactiveRedisTemplate<String,String> reactiveRedisTemplate;

    @Autowired
    private AiPlanService aiPlanService;

    @Autowired
    ObjectMapper objectMapper;

    /**
     * 提交AI生成请求
     */
    @PostMapping("/submit")
    public Mono<ResponseEntity<String>> submitPlan(@RequestBody PlanRequest request) throws NoSuchAlgorithmException, JsonProcessingException {
        String sign = buildReqSign(request);
        String cacheKey = "reqSign:" + sign;
        // ① 先查 Redis
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> {
                    if (cached != null) {
                        if (!cached.startsWith("{")) {
                            // 还是一个 taskId（任务未完成）
                            return Mono.just(ResponseEntity.ok(cached));
                        } else {
                            // 已是完整 JSON 结果
                            log.info("缓存命中");
                            return Mono.just(ResponseEntity.ok(cached));
                        }
                    } else {
                        // 不存在缓存（这个分支不会触发，因为 flatMap 不会为 null）
                        return Mono.empty();
                    }
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("缓存未命中，入队处理 PlanRequest: {}", request);
                    String taskId = aiPlanService.enqueueTask(request);
                    return reactiveRedisTemplate.opsForValue()
                            .set(cacheKey, taskId, Duration.ofMinutes(30))
                            .thenReturn(ResponseEntity.ok(taskId));
                }));
    }

    /**
     * 查询AI生成结果
     */
    @GetMapping("/result/{taskId}")
    public Mono<ResponseEntity<String>> getPlanResult(@PathVariable String taskId) throws JsonProcessingException {
        return aiPlanService.getPlanResult(taskId)
                .map(res -> ResponseEntity.ok(res.toString()))
                .defaultIfEmpty(ResponseEntity.ok("PENDING"))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500).body("解析错误")));

    }

    private String buildReqSign(PlanRequest req) throws JsonProcessingException, NoSuchAlgorithmException {
        // ① 使用 Jackson 排序序列化，确保字段顺序一致
        String json = objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(req);

        // ② MD5/sha256 摘要，避免 key 过长
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);   // e.g. "a3e6b..."
    }


    /**
     * 高吞吐 SSE：前端 EventSource('/api/plan/stream/{taskId}')
     */
    @GetMapping(value = "/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@PathVariable("taskId") String taskId) {
        String topic = "plan-delta:" + taskId;
        String cacheKey = "task:" + taskId;

        // 1. 先从 Redis 缓存拿已有结果，可能是完整结果或者空
        Mono<String> cachedResultMono = reactiveRedisTemplate.opsForValue()
                .get(cacheKey)
                .filter(json -> json != null && !json.isEmpty());

        // 2. 监听频道流
        Flux<String> channelStream = reactiveRedisTemplate.listenToChannel(topic)
                .map(msg -> msg.getMessage())
                .takeUntil("[DONE]"::equals)
                .doFinally(signal -> {
                    // 删除缓存和频道对应的key
//                    reactiveRedisTemplate.delete(cacheKey).subscribe();
//                    reactiveRedisTemplate.delete(topic).subscribe();
                });

        // 3. 合并缓存结果流 + 实时频道流
        // 如果缓存有值，先推送缓存内容，然后接着推送实时消息
        return cachedResultMono
                .flux() // Mono 转 Flux，方便合并
                .concatWith(channelStream)
                .switchIfEmpty(channelStream); // 缓存无数据时直接推频道流
    }
}
    