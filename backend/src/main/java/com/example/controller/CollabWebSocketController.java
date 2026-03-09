package com.example.controller;

import com.example.service.CollabEventService;
import com.example.service.RedisBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CollabWebSocketController {

    private final RedisBroadcastService redisBroadcastService;
    private final CollabEventService collabEventService;

    @MessageMapping("/collab/{docId}")
    @SendTo("/topic/collab/{docId}")
    public byte[] handleUpdate(@DestinationVariable String docId,
                               @Payload byte[] update,
                               Principal principal) {

        UUID docUuid = UUID.fromString(docId);
        UUID userId = UUID.fromString(principal.getName());

        log.debug("Received update for doc: {} from user: {}", docId, userId);

        // Persist the event
        collabEventService.appendEvent(docUuid, userId, update);

        // Broadcast to other instances via Redis
        redisBroadcastService.broadcastUpdate(docId, update);

        return update;
    }
}
