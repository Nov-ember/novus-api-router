package com.example.novusapirouter.server.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.novusapirouter.model.dto.LoginDTO;
import com.example.novusapirouter.model.dto.RegisterDTO;
import com.example.novusapirouter.model.entity.User;

public interface AuthService extends IService<User> {
    String register(RegisterDTO registerDTO);

    String login(LoginDTO loginDTO);

    void logout();
}
