package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @className: RedissonConfig
 * @description:
 * @author: FengL
 * @create: 2025/1/6 22:23
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 创建配置
        Config config = new Config();
        // 添加redis地址 可以使用config.useClusterServers()来设置集群地址
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("123456");
        // 创建客户端
        return Redisson.create(config);
    }
}
