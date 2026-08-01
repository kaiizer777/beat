package com.beat.controller;

import com.beat.service.DynamicSchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal")
public class InternalChannelController {

    private static final Logger log = LoggerFactory.getLogger(InternalChannelController.class);

    @Value("${internal.secret}")
    private String internalSecret;

    private final DynamicSchedulerService dynamicSchedulerService;

    public InternalChannelController(DynamicSchedulerService dynamicSchedulerService) {
        this.dynamicSchedulerService = dynamicSchedulerService;
    }

    @PostMapping("/run-due-channels")
    public ResponseEntity<?> runDueChannels(
            @RequestHeader(value = "X-Internal-Secret", required = false) String headerSecret,
            @RequestHeader(value = "X-Shared-Secret", required = false) String altHeaderSecret) {

        String secretProvided = headerSecret != null ? headerSecret : altHeaderSecret;

        if (secretProvided == null || !internalSecret.equals(secretProvided)) {
            log.warn("Unauthorized access attempt to /api/internal/run-due-channels");
            Map<String, String> errorResp = Map.of("error", "Unauthorized", "message", "Invalid or missing internal secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResp);
        }

        List<Long> triggeredIds = dynamicSchedulerService.processDueChannels();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("triggeredCount", triggeredIds.size());
        response.put("triggeredChannelIds", triggeredIds);

        return ResponseEntity.ok(response);
    }
}
