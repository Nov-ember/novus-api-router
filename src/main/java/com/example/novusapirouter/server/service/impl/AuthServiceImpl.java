package com.example.novusapirouter.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.novusapirouter.common.exception.BusinessException;
import com.example.novusapirouter.common.util.JwtUtils;
import com.example.novusapirouter.model.dto.LoginDTO;
import com.example.novusapirouter.model.dto.RegisterDTO;
import com.example.novusapirouter.model.entity.User;
import com.example.novusapirouter.model.entity.UserWallet;
import com.example.novusapirouter.server.mapper.UserMapper;
import com.example.novusapirouter.server.mapper.WalletMapper;
import com.example.novusapirouter.server.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final WalletMapper walletMapper;

    @Override
    @Transactional
    public String register(RegisterDTO registerDTO) {
        checkEmailUnique(registerDTO.getEmail());
        checkUsernameUnique(registerDTO.getUsername());

        User user = User.builder()
                .username(registerDTO.getUsername())
                .nickname(StringUtils.hasText(registerDTO.getNickname()) ? registerDTO.getUsername() : registerDTO.getNickname())
                .email(registerDTO.getEmail())
                .passwordHash(passwordEncoder.encode(registerDTO.getPassword()))
                .build();
        if (userMapper.insert(user) != 1) {
            throw new BusinessException("用户创建失败");
        }

        UserWallet userWallet = UserWallet.builder()
                .userId(user.getId())
                .build();
        if (walletMapper.insert(userWallet) != 1) {
            throw new BusinessException("用户钱包初始化失败");
        }

        return jwtUtils.createToken(user.getId());
    }

    private void checkEmailUnique(String email) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException("邮箱已存在");
        }
    }

    private void checkUsernameUnique(String username) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw new BusinessException("用户名已存在");
        }
    }

    @Override
    public String login(LoginDTO loginDTO) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(loginDTO.getEmail())) {
            wrapper.eq(User::getEmail, loginDTO.getEmail());
        } else if (StringUtils.hasText(loginDTO.getUsername())) {
            wrapper.eq(User::getUsername, loginDTO.getUsername());
        } else {
            throw new BusinessException("请输入账号信息");
        }

        User user = userMapper.selectOne(wrapper);
        if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("账号信息或密码错误");
        }
        return jwtUtils.createToken(user.getId());
    }

    @Override
    public void logout() {}
}
