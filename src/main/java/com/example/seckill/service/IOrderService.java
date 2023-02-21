package com.example.seckill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.seckill.pojo.Order;
import com.example.seckill.pojo.User;
import com.example.seckill.vo.GoodsVo;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lfb
 * @since 2022-09-13
 */
public interface IOrderService extends IService<Order> {

    Order secKill(User user, GoodsVo goodsVo);

    String createPath(User user, Long goodsId);

    boolean checkPath(User user,Long goodsId,String path);

    boolean checkCaptcha(User user, Long goodsId, String captcha);
}
