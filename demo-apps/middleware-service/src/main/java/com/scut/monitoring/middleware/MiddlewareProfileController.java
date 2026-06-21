package com.scut.monitoring.middleware;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import redis.clients.jedis.Jedis;

import java.time.Instant;
import java.util.Map;

@RestController
public class MiddlewareProfileController {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final OkHttpClient httpClient;

    public MiddlewareProfileController(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.httpClient = new OkHttpClient();
    }

    @GetMapping("/api/middleware/profile")
    public Map<String, Object> profile(@RequestParam(defaultValue = "guest") String user) throws Exception {
        Integer mysqlResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        String redisKey = "middleware:profile:" + user;
        redisTemplate.opsForValue().set(redisKey, Instant.now().toString());
        String redisValue;
        try (Jedis jedis = new Jedis("127.0.0.1", 6379)) {
            jedis.set(redisKey, Instant.now().toString());
            redisValue = jedis.get(redisKey);
        }
        String nginxStatus;
        Request request = new Request.Builder()
                .url("http://127.0.0.1/")
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("nginx returned " + response.code());
            }
            nginxStatus = response.body() == null ? "unknown" : response.body().string();
        }

        return Map.of(
                "service", "middleware-service",
                "user", user,
                "mysql", mysqlResult,
                "redis", redisValue == null ? "missing" : "ok",
                "nginx", nginxStatus == null ? "unknown" : nginxStatus.trim(),
                "timestamp", Instant.now().toString()
        );
    }
}
