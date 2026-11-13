package com.campus.auction.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.auction.entity.User;

public interface UserMapper extends BaseMapper<User> {

    /** 按 openid 查用户 id，不存在返回 null */
    default Long selectIdByOpenid(String openid) {
        User user = selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        return user == null ? null : user.getId();
    }
}
