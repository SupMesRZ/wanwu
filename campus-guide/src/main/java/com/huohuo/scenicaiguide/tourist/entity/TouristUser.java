package com.huohuo.scenicaiguide.tourist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认证游客实体
 */
@Data
@TableName("tourist_user")
public class TouristUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;

    private String nickname;

    private String avatar;

    private String status;

    private String banReason;

    private LocalDateTime banUntil;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

}
