package com.niro.core.exception;


import com.niro.core.result.GlobalMessageConstant;
import com.niro.core.result.StatusCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务异常类
 * @author liyl
 * @date 2025-12-19
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BusinessException extends RuntimeException{

    /**
     * 异常状态码
     */
    private int code;
    /**
     * 异常消息
     */
    private String message;
    
    
    public BusinessException(){
        this.code = StatusCode.FAIL_CODE;
        this.message = GlobalMessageConstant.FAILURE;
    }
    
    public BusinessException(String message){
        this.code = StatusCode.FAIL_CODE;
        this.message = message;
    }

    public BusinessException(int code,String message){
        this.code = code;
        this.message = message;
    }
    
}
