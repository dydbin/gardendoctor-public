package com.project.farming.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class ExternalServicesIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void mysqlConnectionIsAvailable() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {

            assertThat(connection.isValid(1)).isTrue();
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void redisConnectionIsAvailable() {
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        assertThat(connectionFactory).isNotNull();

        RedisConnection connection = connectionFactory.getConnection();
        try {
            assertThat(connection.ping()).isEqualTo("PONG");

            String key = "portfolio:integration-test:redis";
            redisTemplate.opsForValue().set(key, "ok", Duration.ofSeconds(10));
            assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("ok");
            redisTemplate.delete(key);
        } finally {
            connection.close();
        }
    }
}
