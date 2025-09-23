package com.hmdp.dto;

import lombok.Data;

/**
 * 替代Entity用于和前端交互/存储的对象，隐去敏感信息
 */
@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
}
