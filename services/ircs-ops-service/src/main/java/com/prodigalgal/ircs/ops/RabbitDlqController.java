package com.prodigalgal.ircs.ops;

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
public class RabbitDlqController {

    private final RabbitDlqService rabbitDlqService;

    @GetMapping
    public ResponseEntity<List<RabbitDlqQueueResponse>> listQueues() {
        return ResponseEntity.ok(rabbitDlqService.listQueues());
    }

    @PostMapping("/retry")
    public ResponseEntity<RabbitDlqActionResponse> retry(
            @RequestParam String queueName,
            @RequestParam(defaultValue = "1") int limit) {
        return ResponseEntity.ok(rabbitDlqService.retry(queueName, limit));
    }

    @PostMapping("/discard")
    public ResponseEntity<RabbitDlqActionResponse> discard(
            @RequestParam String queueName,
            @RequestParam(defaultValue = "1") int limit) {
        return ResponseEntity.ok(rabbitDlqService.discard(queueName, limit));
    }
}
