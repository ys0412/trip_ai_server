package net.eson.controller;

import net.eson.dto.PlanRequest;
import net.eson.service.AiPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * @author Eson
 * @date 2025年07月07日 15:44
 */
@RestController
@RequestMapping("/api/plan")
public class AiPlanController {

    @Autowired
    private AiPlanService aiPlanService;

    /**
     * 提交AI生成请求
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitPlan(@RequestBody PlanRequest request) {
        String taskId = aiPlanService.enqueueTask(request);
        return ResponseEntity.ok().body(taskId);
    }

    /**
     * 查询AI生成结果
     */
    @GetMapping("/result/{taskId}")
    public ResponseEntity<?> getPlanResult(@PathVariable String taskId) {
        String result = aiPlanService.getPlanResult(taskId);
        if (result == null || result.isEmpty()) {
            return ResponseEntity.ok().body("PENDING");
        }
        return ResponseEntity.ok().body(result);
    }
}
    