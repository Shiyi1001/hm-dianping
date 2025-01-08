package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWoker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWoker redisIdWoker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {

        // 1.查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2. 判断优惠券是否存在
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        // 3.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        // 4.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        // 5.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        // string.intern()  返回串的常量池中，如果池中已经存在该字符串，则返回池中的该字符串的引用，否则将字符串添加到池中并返回字符串的引用
        // 将同步锁加在 userid 对象上 减少不必要的同步锁
        // 事物的提交要放在锁内 避免锁释放后事物还没有提交
        // 事物是通过代理对象才生效
//        synchronized (userId.toString().intern()) {
//
//            // 获取代理对象（事物）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }

        // 获取分布式锁
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = lock.tryLock(1200);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 三个参数 获取锁最大等待时间（期间会重试） 锁自动释放时间  时间单位
        boolean isLock = lock.tryLock();

        if (!isLock) {
            // 获取锁失败
            return Result.fail("不允许重复下单");
        }

        try {
            // 获取代理对象（事物）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            // 手动释放锁
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 6.一人一单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("您已经购买过该优惠券");
        }

        // 7.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1")     // set stock = stock -1
//                .eq("voucher_id", voucherId).eq("stock", voucher.getStock())    // where voucher_id = ? and stock = ?  这种方式过于安全
                .eq("voucher_id", voucherId).gt("stock", 0) // where voucher_id = ? and stock > 0
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        // 8.创建订单
        long orderId = redisIdWoker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 9.返回订单id
        return Result.ok(orderId);
    }
}
