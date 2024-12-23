package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @className: CacheClient
 * @description: redis工具类
 * @author: FengL
 * @create: 2024/12/17 22:33
 */
@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 写入redis
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 设置逻辑过期时间  并写入redis
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWhitLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透
     *
     * @param id
     * @return
     */
    public <R, T> R queryWithPassThrough(String keyPrefix, T id, Class<R> type,
                                         Function<T, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis信息
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            // 3.返回 直接返回
            return JSONUtil.toBean(json, type);
        }
        //判断命中是否是空值
        if (json != null) {
            return null;
        }
        // 4.不存在 根据id查询数据库
        R r = dbFallback.apply(id);
        // 5.不存在 返回错误
        if (r == null) {
            //将空值写入redis 避免缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 6.存在 写入redis中
        this.set(key, r, time, unit);
        // 7.返回
        return r;
    }


    /**
     * 基于逻辑过期解决缓存击穿问题
     *
     * @param id
     * @return
     */
    public <R, T> R queryWithLogicalExpire(String keyPrefix, T id, Class<R> type,
                                           Function<T, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询信息
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isBlank(json)) {
            // 不存在 直接返回
            return null;
        }

        // 3.存在 将json 反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. 判断 是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 4.1未过期  直接返回店铺信息
            return r;
        }

        // 5. 过期  需要缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        // 5.1成功获取互斥锁
        if (tryLock(lockKey)) {
            // 5.2 成功，再次检测redis中缓存是否过期
            json = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(json, RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            expireTime = redisData.getExpireTime();
            // 4. 判断 是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 4.1未过期  直接返回店铺信息
                return r;
            }
            // 5.4  过期 开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                // 重建缓存
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    // 写入redis
                    this.setWhitLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });

        }
        // 7.返回 过期商品信息
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
