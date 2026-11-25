package com.example.ami;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AmiApplicationTests {
    @Test
    void wiresPersistenceAndRedis(@Autowired SqlSessionFactory sessions,
                                 @Autowired DataSource dataSource,
                                 @Autowired StringRedisTemplate redis) {
        assertThat(sessions.getConfiguration().getEnvironment().getDataSource()).isSameAs(dataSource);
        assertThat(redis.getConnectionFactory()).isNotNull();
    }
}
