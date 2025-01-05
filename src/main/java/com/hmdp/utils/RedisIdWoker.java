package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @className: RedisIdWoker
 * @description: redis 生成id
 * @author: FengL
 * @create: 2024/12/24 22:14
 */
@Component
public class RedisIdWoker {

    /**
     * 初始时间戳秒数
     * 1992-04-07 0:0:0
     */
    private static final long BEGIN_TIMESTAMP = 702604800L;

    /**
     * 序列号位数
     */
    private static final int COUNT_BITS = 32;


    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 时间戳 32位 + 序列号 32位
     *
     * @param keyPrefix
     * @return
     */
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1 获取当前日期 精确到天  防止超过最大增长数值 2^64
        // yyyy:MM:dd  方便后面统计 年 月 日 生成的订单数
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("inc:" + keyPrefix + ":" + date);

        // 3.拼接连接在一起
        return timestamp << COUNT_BITS | increment;
    }

    public static void main(String[] args) {
        LocalDateTime localDateTime = LocalDateTime.of(1992, 4, 7, 0, 0, 0);
        long second = localDateTime.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}
