package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 * 优惠券秒杀服务实现类
 * 下单功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private IVoucherService voucherService;
    @Resource
    private RedisWorker redisWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> secKill_script;

    static {
        secKill_script = new DefaultRedisScript<>();
        secKill_script.setLocation(new ClassPathResource("seckill.lua"));
        secKill_script.setResultType(Long.class);
    }

    private Result secKillVoucherWithCache(Long voucherId){
        //执行lua
        UserDTO user = UserHolder.getUser();
        Long result = stringRedisTemplate.execute(secKill_script, Collections.emptyList(), voucherId.toString(), user.getId().toString());
        //判断结果
        int r = result.intValue();
        //不为0，代表没有抢购资格
        if(r != 0){
            return Result.fail(r==1?"库存不足":"已经购买");
        }
        //为0，把下单信息保存到队列

        //返回订单id
        long orderId = redisWorker.nextId("order");
        return Result.ok(orderId);
    }
    @Override
    public Result secKillVoucher(Long voucherId) {
        //查询优惠券
        Voucher voucher = voucherService.getById(voucherId);
        //判断秒杀开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        //判断秒杀结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        //判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        //下单逻辑
        String userId = UserHolder.getUser().getId().toString();
        //获取分布式锁
        SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        boolean lock = redisLock.tryLock(20);
        //拿锁失败
        if (!lock) {
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象处理事务
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createOrder(voucherId);
        } finally {
            redisLock.unlock();
        }
    }

    /**
     * 创建订单
     */
    @Transactional
    public Result createOrder(Long voucherId) {
        //一人一单
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        //一个用户，对一个订单的数量
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            return Result.fail("已经购买");
        }
        //CAS 扣减库存
        boolean success = voucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) //扣减库存之前查询数据数量
                .update();
        //创建订单
        if (success) {
            long orderId = redisWorker.nextId("order");
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(orderId);
            voucherOrder.setUserId(userId);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            return Result.ok();
        }
        return Result.fail("下单失败");
    }
}
