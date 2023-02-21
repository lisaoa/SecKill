package com.example.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.seckill.mapper.OrderMapper;
import com.example.seckill.pojo.*;
import com.example.seckill.service.IGoodsService;
import com.example.seckill.service.IOrderService;
import com.example.seckill.service.ISeckillGoodsService;
import com.example.seckill.service.ISeckillOrderService;
import com.example.seckill.utils.MD5Util;
import com.example.seckill.utils.UUIDUtil;
import com.example.seckill.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lfb
 * @since 2022-09-13
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {
    @Autowired
    private ISeckillGoodsService seckillGoodsService;
    @Autowired
    private ISeckillOrderService seckillOrderService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;

    @Override
    @Transactional
    public Order secKill(User user, GoodsVo goodsVo) {
        //秒杀商品表减库存
        SeckillGoods seckillGoods = seckillGoodsService.getOne(new QueryWrapper<SeckillGoods>().
                eq("goods_id", goodsVo.getId()));
        seckillGoods.setStockCount(seckillGoods.getStockCount() - 1);
        //seckillGoodsService.updateById(seckillGoods);

        //此处也进行一次判断是为了减少sql语句的执行
        if(seckillGoods.getStockCount() < 0){
            return null;
        }

        /**
         * 以下语句不可靠，因为getStockCount()不准确，例子：
         * 45行语句中，由于多线程并发或并行，会导致多个线程同时设置StockCount为1，
         * 此时执行下面语句，卖出两件商品，库存只是从2变为1
         */
//        seckillGoodsService.update(new UpdateWrapper<SeckillGoods>().
//                set("stock_count",seckillGoods.getStockCount())
//                .eq("id",seckillGoods.getId())
//                .gt("stock_count",0));
        boolean updateResult = seckillGoodsService.update(new UpdateWrapper<SeckillGoods>().
                setSql("stock_count = stock_count - 1").
                eq("goods_id", goodsVo.getId()).
                gt("stock_count", 0));

        //如果不加这个判断语句，尽管上方的update语句执行失败，也会生成order订单
        if(!updateResult){
            redisTemplate.opsForValue().set("isStockEmpty:" + goodsVo.getId(),true);
            return null;
        }

        //生成订单
        Order order = new Order();
        order.setUserId(user.getId());
        order.setGoodsId(goodsVo.getId());
        order.setDeliveryAddrId(0L);
        order.setGoodsName(goodsVo.getGoodsName());
        order.setGoodsCount(1);
        order.setGoodsPrice(seckillGoods.getSeckillPrice());
        order.setOrderChannel(1);
        order.setStatus(0);
        order.setCreateDate(new Date());
        //插入成功后会自动返回order的主键id
        orderMapper.insert(order);
        //生成秒杀订单
        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setUserId(user.getId());
        seckillOrder.setOrderId(order.getId());
        seckillOrder.setGoodsId(goodsVo.getId());
        seckillOrderService.save(seckillOrder);

        redisTemplate.opsForValue().set("order:" + seckillOrder.getUserId() + ":" + seckillOrder.getGoodsId(),
                seckillOrder);

        return order;
    }


    /**
     * 生成秒杀地址，并将秒杀地址存放到redis中，并设置过期时间
     * @param user
     * @param goodsId
     * @return
     */
    @Override
    public String createPath(User user, Long goodsId) {
        String path = MD5Util.md5(UUIDUtil.uuid() + "123456");
        redisTemplate.opsForValue().set("seckillPath:" + user.getId() + ":" + goodsId,
                path,180, TimeUnit.SECONDS);
        return path;
    }

    /**
     * 验证秒杀地址是否正确
     */
    @Override
    public boolean checkPath(User user,Long goodsId,String path){
        if(user == null || !StringUtils.hasText(path)){
            return false;
        }
        String redisPath = ((String) redisTemplate.opsForValue().get("seckillPath:" + user.getId() + ":" + goodsId));
        return path.equals(redisPath);

    }

    @Override
    public boolean checkCaptcha(User user, Long goodsId, String captcha) {
        if(user == null || !StringUtils.hasText(captcha)) return false;
        String redisCaptcha = ((String) redisTemplate.opsForValue().get("captcha:" + user.getId() + ":" + goodsId));
        log.error("查询出来的redisCaptcha:" + redisCaptcha);
        return captcha.equals(redisCaptcha);
    }
}
