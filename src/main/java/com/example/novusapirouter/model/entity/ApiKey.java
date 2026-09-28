package com.example.novusapirouter.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("api_key")
public class ApiKey {

    public static final int ACTIVE = 1;
    public static final int REVOKED = 0;

    @TableId
    private Long id;
    private Long userId;
    private String name;
    private String keyHash;
    private String keySuffix;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
