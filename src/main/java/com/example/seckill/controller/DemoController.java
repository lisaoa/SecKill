package com.example.seckill.controller;

import com.example.seckill.vo.RespBean;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
public class DemoController {
    @RequestMapping("/hello")
    public RespBean hello(Model model){
        model.addAttribute("name","张三");
        return RespBean.success();
    }
}
