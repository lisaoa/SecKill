package com.example.seckill.controller;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.seckill.exception.GlobalException;
import com.example.seckill.pojo.Order;
import com.example.seckill.pojo.SeckillMessage;
import com.example.seckill.pojo.SeckillOrder;
import com.example.seckill.pojo.User;
import com.example.seckill.rabbitmq.MQSender;
import com.example.seckill.service.IGoodsService;
import com.example.seckill.service.IOrderService;
import com.example.seckill.service.ISeckillOrderService;
import com.example.seckill.vo.GoodsVo;
import com.example.seckill.vo.RespBean;
import com.example.seckill.vo.RespBeanEnum;
import com.sun.org.apache.xpath.internal.operations.Bool;
import com.wf.captcha.ArithmeticCaptcha;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/secKill")
@Slf4j
public class SecKillController implements InitializingBean {
    @Autowired
    private IGoodsService goodsService;
    @Autowired
    private ISeckillOrderService seckillOrderService;
    @Autowired
    private IOrderService orderService;
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Autowired
    private MQSender mqSender;
    @Autowired
    private DefaultRedisScript<Long> script;

    //????????????????????????redis?????????
    private Map<Long, Boolean> EmptyStockMap = new HashMap<>();

    @RequestMapping("/{path}/doSecKill")
    @ResponseBody
    //????????????goodsId???????????????restful??????
    public Object doSecKill(ModelAndView model, User user, Long goodsId, @PathVariable String path){
        if(user == null){
            model.setViewName("login");
            return model;
        }
        model.addObject("user",user);

        ValueOperations<String, Object> operations= redisTemplate.opsForValue();

        //?????????????????????????????????

        boolean check = orderService.checkPath(user, goodsId, path);
        log.error("url?????????????????????" + check);
        if(!check){
            return RespBean.error(RespBeanEnum.REQUEST_ILLEGAL);
        }


        //????????????????????????
        SeckillOrder seckillOrder = (SeckillOrder) redisTemplate.opsForValue().
                get("order:" + user.getId() + ":" + goodsId);
        if(seckillOrder != null){
            model.addObject("errMsg",RespBeanEnum.REPEAT_ERROR.getMessage());
            model.setViewName("secKillFail");
            return model;
        }

        //?????????????????????redis??????
        if(EmptyStockMap.get(goodsId)){
            model.addObject("errMsg",RespBeanEnum.EMPTY_STOCK.getMessage());
            model.setViewName("secKillFail");
            return model;
        }


        //????????????????????????????????????????????????????????????
        //????????????????????????????????????lua???????????????
        Long stock = operations.decrement("seckillGoods:" + goodsId);
//        Long stock = redisTemplate.execute(script, Collections.singletonList("seckillGoods" + goodsId),
//                Collections.EMPTY_LIST);
        if(stock < 0){
            EmptyStockMap.put(goodsId,true);
            operations.increment("seckillGoods:" + goodsId);
            model.addObject("errMsg",RespBeanEnum.EMPTY_STOCK.getMessage());
            model.setViewName("secKillFail");
            return model;
        }
        //??????????????????RabbitMQ???????????????????????????....
        SeckillMessage message = new SeckillMessage(user,goodsId);
        mqSender.send(JSON.toJSONString(message));

        //???????????????
        return RespBean.success(goodsId);
        /*
        //????????????id?????????????????????????????????????????????????????????
        GoodsVo goodsVo = goodsService.findGoodsVoByGoodsId(goodsId);
        //????????????
        if(goodsVo.getStockCount() < 1){
            model.addAttribute("errMsg", RespBeanEnum.EMPTY_STOCK.getMessage());
            //??????????????????
            return "secKillFail";
        }
        //????????????????????????
//        SeckillOrder seckillOrder =
//                seckillOrderService.getOne(new QueryWrapper<SeckillOrder>()
//                        .eq("user_id",user.getId())
//                        .eq("goods_id",goodsId));

        //???redis?????????
        SeckillOrder seckillOrder = (SeckillOrder) redisTemplate.opsForValue().
                get("order:" + user.getId() + ":" + goodsVo.getId());
        if(seckillOrder != null){
            model.addAttribute("errMsg",RespBeanEnum.REPEAT_ERROR.getMessage());
            return "secKillFail";
        }
        //????????????????????????
        Order order = orderService.secKill(user,goodsVo);

        model.addAttribute("goods",goodsVo);
        model.addAttribute("order",order);
        return "orderDetail";
         */
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //?????????????????????????????????redis???
        List<GoodsVo> goods = goodsService.findGoodsVo();
        if (CollectionUtils.isEmpty(goods)) {
            return;
        }
        goods.forEach(good->{
            EmptyStockMap.put(good.getId(),false);
            redisTemplate.opsForValue().set("seckillGoods:" + good.getId(),good.getStockCount());
        });

    }

    @RequestMapping(value = "/result",method = RequestMethod.GET)
    /**
     * ??????????????????
     */
    @ResponseBody
    public RespBean result(User user,Long goodsId){
        if(user == null){
            return RespBean.error(RespBeanEnum.USER_NOT_LOGIN);
        }
        Long orderId = seckillOrderService.getResult(user,goodsId);
        return RespBean.success(orderId);
    }


    @RequestMapping(value = "/path",method = RequestMethod.GET)
    @ResponseBody
    public RespBean getPath(User user, Long goodsId, String captcha, HttpServletRequest request){
        if(user == null){
            return RespBean.error(RespBeanEnum.USER_NOT_LOGIN);
        }
        //????????????????????????????????????
        //5???????????????????????????
        ValueOperations<String, Object> valueOperations = redisTemplate.opsForValue();
        String uri = request.getRequestURI();
        Integer count = (Integer) valueOperations.get(uri + ":" + user.getId());
        if(count == null){
            valueOperations.set(uri + ":" + user.getId(),1,5,TimeUnit.SECONDS);
        }else if(count < 5){
            valueOperations.increment(uri + ":" + user.getId());
        }else{
            return RespBean.error(RespBeanEnum.ACCESS_LIMIT);
        }

        //???????????????????????????
        boolean check = orderService.checkCaptcha(user,goodsId,captcha);
        if(!check){
            return RespBean.error(RespBeanEnum.ERROR_CAPTCHA);
        }

        String path = orderService.createPath(user,goodsId);
        return RespBean.success(path);
    }

    /**
     * ???????????????
     */
    @RequestMapping(value = "/captcha",method = RequestMethod.GET)
    public void verifyCode(User user, Long goodsId, HttpServletResponse response){
        if(null == user){
            throw new GlobalException(RespBeanEnum.USER_NOT_LOGIN);
        }
        //????????????????????????????????????
        response.setContentType("image/gif");
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);

        // ????????????
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(130, 32);
        captcha.setLen(3);  // ?????????????????????????????????
        //????????????????????????Redis?????????
        redisTemplate.opsForValue().set("captcha:" + user.getId() + ":" + goodsId,
                captcha.text(),300, TimeUnit.SECONDS);


        try {
            captcha.out(response.getOutputStream());  // ???????????????
        } catch (IOException e) {
            log.error("????????????????????????{}",e.getMessage());
        }
    }
}
