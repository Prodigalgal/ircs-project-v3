package com.prodigalgal.ircs.ops.queue.dlq.broker;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ops/rabbit-dlq")
public class DeadLetterController {

    private final DeadLetterService rabbitDlqService;

    @GetMapping
    public ResponseEntity<List<DeadLetterQueueResponse>> listQueues(
            @RequestParam(defaultValue = "0") int sampleLimit) {
        return ResponseEntity.ok(rabbitDlqService.listQueues(sampleLimit));
    }

    @PostMapping("/retry")
    public ResponseEntity<DeadLetterActionResponse> retry(
            @RequestParam String queueName,
            @RequestParam(defaultValue = "1") int limit) {
        return ResponseEntity.ok(rabbitDlqService.retry(queueName, limit));
    }

    @PostMapping("/discard")
    public ResponseEntity<DeadLetterActionResponse> discard(
            @RequestParam String queueName,
            @RequestParam(defaultValue = "1") int limit) {
        return ResponseEntity.ok(rabbitDlqService.discard(queueName, limit));
    }
}
