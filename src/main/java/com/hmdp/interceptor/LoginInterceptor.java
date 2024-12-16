package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @className: LoginInterceptor
 * @description: 用户登录拦截器
 * @author: FengL
 * @create: 2024/12/11 21:48
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 进入controller 层前进行处理
     * 将用户信息保存在ThreadLocal 中
     *
     * @param request
     * @param response
     * @param handler
     * @return
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    /*
     session 方式
    // 1.获取session
        HttpSession session = request.getSession();
        // 2.获取session 中用户信息
        Object user = session.getAttribute("user");
        // 3.判断用户是否存在 不存在，拦截
        if (user == null) {
            response.setStatus(401);
            return false;
        }

        // 4.存在，保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        // 5.放行
        return true;*/

/*        // 1. 从请求头中获取token
        String token = request.getHeader("authorization");
        // 2.根据token 从redis中获取user 信息
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        // 3. 判断用户是否存在 不存在 进行拦截
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }
        // 4. 存在 将用户信息 map转成userDto 存入ThreadLocal 中
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        // 5. 刷新token 令牌过期时间
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 6. 放行
        return true;*/

        // 1. 从ThreadLocal 中获取 用户心信息 不存在 拦截
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            response.setStatus(401);
            return false;
        }
        // 2. 存在 放行
        return true;
    }

}
