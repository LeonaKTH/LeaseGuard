package com.drone.rental.config;
import com.drone.rental.entity.User;
import com.drone.rental.mapper.UserMapper;
import com.drone.rental.security.PasswordPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration @Profile("local-bootstrap")
public class LocalBootstrap {
 @Bean ApplicationRunner bootstrap(UserMapper users,PasswordEncoder passwords,@Value("${LEASEGUARD_BOOTSTRAP_USER}") String name,@Value("${LEASEGUARD_BOOTSTRAP_PASSWORD}") String credential){return args->{
  PasswordPolicy.validate(credential);
  if(users.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername,name))>0)throw new IllegalStateException("Bootstrap refuses overwriting existing account");
  User u=new User();u.setUsername(name);u.setNickname("本地演示管理员");u.setPassword(passwords.encode(credential));u.setRole(1);u.setStatus(1);u.setCreditStatus(1);users.insert(u);
 };}
}
