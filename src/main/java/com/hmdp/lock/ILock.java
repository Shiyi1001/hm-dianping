package com.hmdp.lock;

/**
 * @className: ILock
 * @description:
 * @author: FengL
 * @create: 2025/1/5 21:10
 */
public interface ILock {

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁持有超时时间，过期后自动锁释放 防止死锁
     * @return true 获取成功，false获取失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();
}
