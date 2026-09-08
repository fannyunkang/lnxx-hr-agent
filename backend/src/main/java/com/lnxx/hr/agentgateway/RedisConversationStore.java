package com.lnxx.hr.agentgateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisConversationStore {
    private static final TypeReference<List<Message>> TYPE = new TypeReference<>() {};
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final int maxMessages;
    private final Duration ttl;
    private final Map<String, List<Message>> fallback = new ConcurrentHashMap<>();

    public RedisConversationStore(StringRedisTemplate redis, ObjectMapper json,
                                  @Value("${hr-agent.memory.max-messages:20}") int maxMessages,
                                  @Value("${hr-agent.memory.ttl:PT24H}") Duration ttl) {
        this.redis = redis; this.json = json; this.maxMessages = maxMessages; this.ttl = ttl;
    }

    public List<Message> get(String key) {
        try {
            String value = redis.opsForValue().get(redisKey(key));
            return value == null ? List.of() : json.readValue(value, TYPE);
        } catch (Exception ignored) {
            return List.copyOf(fallback.getOrDefault(key, List.of()));
        }
    }

    public void append(String key, String user, String assistant) {
        List<Message> messages = new ArrayList<>(get(key));
        messages.add(new Message("user", user)); messages.add(new Message("assistant", assistant));
        if (messages.size() > maxMessages) messages = new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size()));
        fallback.put(key, List.copyOf(messages));
        try { redis.opsForValue().set(redisKey(key), json.writeValueAsString(messages), ttl); } catch (Exception ignored) { }
    }

    public void clear(String key) {
        fallback.remove(key);
        try { redis.delete(redisKey(key)); } catch (Exception ignored) { }
    }

    private String redisKey(String key) { return "hr-agent:conversation:" + key; }
    public record Message(String role, String content) {}
}
