package com.example.seckill.controller;

import com.example.seckill.exception.GlobalException;
import com.example.seckill.pojo.User;
import com.example.seckill.service.IGoodsService;
import com.example.seckill.service.IUserService;
import com.example.seckill.vo.DetailVo;
import com.example.seckill.vo.GoodsVo;
import com.example.seckill.vo.RespBean;
import com.example.seckill.vo.RespBeanEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring5.view.ThymeleafViewResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/goods")
public class GoodsController {
    @Autowired
    private IUserService userService;
    @Autowired
    private IGoodsService goodsService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private ThymeleafViewResolver thymeleafViewResolver;

    @RequestMapping(value = "/toList",produces = "text/html;charset=utf-8")
    @ResponseBody
    public String toList(Model model, User user,
                         HttpServletRequest request,HttpServletResponse response){
//        if(StringUtils.isEmpty(ticket)){
//            return "login";
//        }
//
//        //此处也不能再从session中获取用户，而是从redis中获取
//        //User user = (User) session.getAttribute(ticket);
//        User user = userService.getUserByCookie(ticket, request, response);
//
        //用户不登录也能看到商品列表，所以此处不需要return login页面？
        //if(user == null) return "login";

        //Redis中获取页面，如果不为null，直接返回页面
        ValueOperations valueOperations = redisTemplate.opsForValue();
        String html = ((String) valueOperations.get("goodsList"));
        if(!StringUtils.isEmpty(html)){
            return html;
        }
        //如果为null，手动渲染，存入redis并返回
        model.addAttribute("user",user);
        model.addAttribute("goodsList",goodsService.findGoodsVo());

        WebContext context = new WebContext(request, response,
                request.getServletContext(), request.getLocale(),model.asMap());
        html = thymeleafViewResolver.getTemplateEngine().process("goodsList",context);
        if(StringUtils.hasText(html)){
            //设置过期时间为一分钟
            valueOperations.set("goodsList",html,60, TimeUnit.SECONDS);
        }
        return html;
    }


    /**
     * 根据商品id查询具体的商品
     */
    @RequestMapping("/toDetail2/{goodsId}")
    @ResponseBody
    public RespBean toDetail2(Model model, @PathVariable Long goodsId, User user) throws IOException {
        //此处会返回字符串？？
        //返回错误代码，让前端进行页面跳转
        if(user == null) return RespBean.error(RespBeanEnum.USER_NOT_LOGIN);

        GoodsVo goodsVo = goodsService.findGoodsVoByGoodsId(goodsId);

        Date startDate = goodsVo.getStartDate();
        Date endDate = goodsVo.getEndDate();
        Date nowDate = new Date();

        int continueSeconds = (int)((endDate.getTime() - startDate.getTime()) / 1000);
        if(continueSeconds <= 0){
            throw new GlobalException(RespBeanEnum.ERROR);
        }

        //秒杀状态
        int secKillStatus;
        //秒杀倒计时
        int remainSeconds = 0;
        if(nowDate.before(startDate)){
            //未开始
            secKillStatus = 0;
            remainSeconds = (int)((startDate.getTime() - nowDate.getTime()) / 1000);
        }else if(nowDate.after(endDate)){
            //秒杀结束
            secKillStatus = 2;
            remainSeconds = -1;
        }else{
            //秒杀进行中
            secKillStatus = 1;
        }

        DetailVo detailVo = new DetailVo();
        detailVo.setUser(user);
        detailVo.setGoodsVo(goodsVo);
        detailVo.setSecKillStatus(secKillStatus);
        detailVo.setRemainSeconds(remainSeconds);
        detailVo.setContinueSeconds(continueSeconds);

        return RespBean.success(detailVo);
    }


    /**
     * 根据商品id查询具体的商品
     */
    @RequestMapping(value = "/toDetail/{goodsId}",produces = "text/html;charset=utf-8")
    @ResponseBody
    public Object toDetail(Model model,@PathVariable Long goodsId,User user,
                           HttpServletRequest request,HttpServletResponse response) throws IOException {
        //此处会返回字符串？？
        if(user == null) return new ModelAndView("login");

        ValueOperations valueOperations = redisTemplate.opsForValue();
        String html = (String) valueOperations.get("goodsDetail:" + goodsId);
        if(StringUtils.hasText(html)){
            return html;
        }

        model.addAttribute("user",user);
        GoodsVo goodsVo = goodsService.findGoodsVoByGoodsId(goodsId);
        model.addAttribute("goods",goodsVo);

        Date startDate = goodsVo.getStartDate();
        Date endDate = goodsVo.getEndDate();
        Date nowDate = new Date();

        int continueSeconds = (int)((endDate.getTime() - startDate.getTime()) / 1000);
        if(continueSeconds <= 0){
            throw new GlobalException(RespBeanEnum.ERROR);
        }else{
            model.addAttribute("continueSeconds",continueSeconds);
        }

        //秒杀状态
        int secKillStatus;
        //秒杀倒计时
        int remainSeconds = 0;
        if(nowDate.before(startDate)){
            //未开始
            secKillStatus = 0;
            remainSeconds = (int)((startDate.getTime() - nowDate.getTime()) / 1000);
        }else if(nowDate.after(endDate)){
            //秒杀结束
            secKillStatus = 2;
            remainSeconds = -1;
        }else{
            //秒杀进行中
            secKillStatus = 1;
        }
        model.addAttribute("remainSeconds",remainSeconds);
        model.addAttribute("secKillStatus",secKillStatus);

        WebContext context = new WebContext(request, response, request.getServletContext(),
                request.getLocale(), model.asMap());
        html = thymeleafViewResolver.getTemplateEngine().process("goodsDetail", context);
        if(StringUtils.hasText(html)){
            valueOperations.set("goodsDetail:" + goodsId,html,60,TimeUnit.SECONDS);
        }

        return html;
    }
}
