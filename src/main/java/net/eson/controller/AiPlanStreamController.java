package net.eson.controller;

import net.eson.service.AiPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * @author Eson
 * @date 2025年07月11日 13:43
 */
@RestController
@RequestMapping("/api/stream")
public class AiPlanStreamController {

    @Autowired
    ReactiveRedisTemplate<String,String> reactiveRedisTemplate;

    @Autowired
    private AiPlanService aiPlanService;

    /**
     * 高吞吐 SSE：前端 EventSource('/api/plan/stream/{taskId}')
     */
    @GetMapping(value = "/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@PathVariable String taskId) {

        String topic = "plan-delta:" + taskId;

        return reactiveRedisTemplate.listenToChannel(topic)                 // 订阅增量
                .map(msg -> msg.getMessage())                       // 获取内容
                .takeUntil("[DONE]"::equals)                        // 收到结束标识即完成
                .doFinally(sig -> reactiveRedisTemplate.unlink(topic).subscribe());
    }
}
    