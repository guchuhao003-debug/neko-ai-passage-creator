package com.wenxi.nekoaipassage.common;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 删除请求包装类
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    @NotNull(message = "id 不能为空")
    private Long id;

    private static final long serialVersionUID = 1L;
}
