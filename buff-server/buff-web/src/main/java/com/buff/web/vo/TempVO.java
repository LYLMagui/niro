package com.buff.web.vo;

import lombok.Data;
import java.io.Serializable;

/**
 * @author liyl
 * @date 2025-12-18
 * @description 临时VO
 */
@Data
public class TempVO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 名称
     */
    private String name;
}
