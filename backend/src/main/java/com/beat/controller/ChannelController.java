package com.beat.controller;

import com.beat.dto.ChannelRequest;
import com.beat.dto.ChannelResponse;
import com.beat.service.ChannelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @PostMapping
    public ResponseEntity<ChannelResponse> createChannel(@Valid @RequestBody ChannelRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        ChannelResponse response = channelService.createChannel(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ChannelResponse>> getAllChannels(@AuthenticationPrincipal Jwt jwt) {
        List<ChannelResponse> response = channelService.getAllChannelsForUser(jwt.getSubject());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChannelResponse> getChannelById(@PathVariable Long id,
                                                           @AuthenticationPrincipal Jwt jwt) {
        ChannelResponse response = channelService.getChannelByIdForUser(id, jwt.getSubject());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChannelResponse> updateChannel(@PathVariable Long id,
                                                         @Valid @RequestBody ChannelRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        ChannelResponse response = channelService.updateChannelForUser(id, request, jwt.getSubject());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long id,
                                               @AuthenticationPrincipal Jwt jwt) {
        channelService.deleteChannelForUser(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}

