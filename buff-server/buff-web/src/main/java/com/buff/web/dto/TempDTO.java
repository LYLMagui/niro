package com.buff.web.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * @author liyl
 * @date 2025-12-18
 * @description 临时DTO
 */
@Data
public class TempDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 名称
     */
    private String name;
}
