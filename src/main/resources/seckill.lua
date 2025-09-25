-- 参数列表
-- 1.优惠券id
local voucherId = ARGV[1];
--2. 用户id
local userId = ARGV[2];
--3.订单id
local orderId = ARGV[3];


-- 库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId

-- 业务脚本
if(tonumber(redis.call('get',stockKey)) <= 0) then
    -- 库存不足，返回1
    return 1
end
-- 判断用户是否已经下单
if(redis.call('sismember',orderKey,userId)==1)then
    -- 存在，说明重复下单
    return 2
end
-- 扣减库存
redis.call('incrby',stockKey,-1)
-- 下单
redis.call('sadd',orderKey,userId)
-- 发送下单消息到队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0