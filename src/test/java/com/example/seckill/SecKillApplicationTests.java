package com.example.seckill;

import com.example.seckill.pojo.User;
import com.example.seckill.service.IUserService;
import com.example.seckill.utils.MD5Util;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

@SpringBootTest
class SecKillApplicationTests {
    @Autowired
    IUserService userService;

    @Test
    void contextLoads() {
        List<User> users = new ArrayList<>();
        for(int i = 0; i < 5000; i++){
            User user = new User();
            user.setId(13000000000L + i);
            user.setNickname("user"+ i);
            user.setSalt("1azb3c4d");
            user.setPassword(MD5Util.inputPassToDBPass("123456",user.getSalt()));

            users.add(user);
        }

        //插入数据库
        for(User user : users){
            userService.save(user);
        }
    }

}
