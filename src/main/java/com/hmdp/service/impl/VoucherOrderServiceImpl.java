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
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    //异步处理下单任务
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024);
    static {
        secKill_script = new DefaultRedisScript<>();
        secKill_script.setLocation(new ClassPathResource("seckill.lua"));
        secKill_script.setResultType(Long.class);
    }
    private IVoucherOrderService proxy;

    @PostConstruct
    private void init(){
        executor.submit(()->{
            while (true){
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    //处理下单任务的逻辑
    private void handleVoucherOrder(VoucherOrder voucherOrder){
        //获取用户
        Long userId = voucherOrder.getUserId();
        //获取分布式锁
        //创建锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        //获取锁
        boolean lock = redisLock.tryLock(20);
        //锁失败
        if (!lock){
            log.error("不可重复下单");
            return;
        }
        try {
            proxy.createOrder(voucherOrder);
        }finally {
            redisLock.unlock();
        }
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
        long orderId = redisWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(user.getId());
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderId);
        orderTasks.offer(voucherOrder);
        //返回订单id
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
        return secKillVoucherWithCache(voucherId);
    }

    /**
     * 创建订单
     */
    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        //一人一单
        Long userId = voucherOrder.getUserId();
        //一个用户，对一个订单的数量
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            return;
        }
        //CAS 扣减库存
        boolean success = voucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) //扣减库存之前查询数据数量
                .update();
        //创建订单
        if (success) {
            log.error("用户已经购买过一次");
            save(voucherOrder);
        }
        log.error("库存不足");
    }
}
