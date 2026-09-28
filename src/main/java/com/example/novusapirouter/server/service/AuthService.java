package com.example.novusapirouter.server.service;

import com.example.novusapirouter.model.dto.LoginDTO;
import com.example.novusapirouter.model.dto.RegisterDTO;

public interface AuthService {
    String register(RegisterDTO registerDTO);

    String login(LoginDTO loginDTO);

    void logout();
}
