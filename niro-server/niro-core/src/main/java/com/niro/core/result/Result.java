package com.niro.core.result;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 全局统计返回结果对象
 * @author liyl
 * @date 2025-12-19
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class Result<T> implements Serializable {
    // 状态码
    private int code;

    // 提示信息
    private String message;

    // 返回数据
    private T data;


    /**
     * 请求成功
     * @param data
     * @return Result
     * @param <T>
     */
    public static <T> Result<T> success(T data) {
        return new Result<T>().setCode(StatusCode.SUCCESS_CODE).setMessage(GlobalMessageConstant.SUCCESS).setData(data);
    }

    /**
     * 请求成功
     * @return
     * @param <T>
     */
    public static <T> Result<T> success(){
        return new Result<T>().setCode(StatusCode.SUCCESS_CODE).setMessage(GlobalMessageConstant.SUCCESS);
    }


    /**
     * 请求异常
     * @return
     * @param <T>
     */
    public static <T> Result<T> failure(){
        return new Result<T>().setCode(StatusCode.FAIL_CODE).setMessage(GlobalMessageConstant.FAILURE);
    }

    /**
     * 请求异常
     * @param message
     * @return
     * @param <T>
     */
    public static <T> Result<T> failure(String message){
        return new Result<T>().setCode(StatusCode.FAIL_CODE).setMessage(message);
    }

    /**
     * 请求异常
     * @param message
     * @return
     * @param <T>
     */
    public static <T> Result<T> failure(int code,String message){
        return new Result<T>().setCode(code).setMessage(message);
    }


}
