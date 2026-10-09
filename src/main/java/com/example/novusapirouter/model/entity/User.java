package com.example.novusapirouter.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@TableName("user")
public class User {
    @TableId
    private Long id;
    private String username;
    private String nickname;
    private Integer gender;
    private String avatar;
    private String email;
    private String passwordHash;
    private Integer activeKeyCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
