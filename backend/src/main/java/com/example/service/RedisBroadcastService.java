package com.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisBroadcastService implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String CHANNEL_PREFIX = "collab:";

    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL_PREFIX + "*"));
    }

    public void broadcastUpdate(String docId, byte[] update) {
        String channel = CHANNEL_PREFIX + docId;
        redisTemplate.convertAndSend(channel, update);
        log.debug("Broadcast update to channel: {}", channel);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String docId = channel.substring(CHANNEL_PREFIX.length());
        byte[] update = message.getBody();

        log.debug("Received update from Redis for doc: {}", docId);
        messagingTemplate.convertAndSend("/topic/collab/" + docId, update);
    }
}
