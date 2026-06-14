package com.prodigalgal.ircs.config;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ConfigController {

    private final ConfigService configService;

    @GetMapping("/configs")
    public Page<SystemConfigSummary> listConfigs(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String keyword
    ) {
        return configService.listConfigs(pageable, keyword);
    }

    @GetMapping("/configs/{key}")
    public ResponseEntity<SystemConfigSummary> getConfig(@PathVariable String key) {
        return configService.findConfig(key)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/configs")
    public ResponseEntity<SystemConfigSummary> createConfig(@Valid @RequestBody SystemConfigWriteRequest request) {
        SystemConfigSummary result = configService.createConfig(request);
        return ResponseEntity
                .created(URI.create("/api/v1/configs/" + result.key()))
                .body(result);
    }

    @PutMapping("/configs/{key}")
    public ResponseEntity<SystemConfigSummary> updateConfig(
            @PathVariable String key,
            @Valid @RequestBody SystemConfigWriteRequest request) {
        return configService.updateConfig(key, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/configs/{key}")
    public ResponseEntity<Void> deleteConfig(@PathVariable String key) {
        configService.deleteConfig(key);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/configs/test/{type}")
    public ResponseEntity<Map<String, String>> testConnection(
            @PathVariable String type,
            @RequestBody(required = false) Map<String, Object> params) {
        return ResponseEntity.ok(configService.testConnection(type, params == null ? Map.of() : params));
    }
}
