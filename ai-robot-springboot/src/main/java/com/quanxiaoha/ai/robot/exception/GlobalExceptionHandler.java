package com.quanxiaoha.ai.robot.exception;

import com.quanxiaoha.ai.robot.enums.ResponseCodeEnum;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.Optional;

/**
 * @author: 犬小哈
 * @url: www.quanxiaoha.com
 * @date: 2023-08-15 10:14
 * @description: 全局异常处理
 **/
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获自定义业务异常
     * @return
     */
    @ExceptionHandler({ BizException.class })
    @ResponseBody
    public Response<Object> handleBizException(HttpServletRequest request, BizException e) {
        log.warn("{} request fail, errorCode: {}, errorMessage: {}", request.getRequestURI(), e.getErrorCode(), e.getErrorMessage());
        return Response.fail(e);
    }

    /**
     * 捕获参数校验异常
     * @return
     */
    @ExceptionHandler({ MethodArgumentNotValidException.class })
    @ResponseBody
    public Response<Object> handleMethodArgumentNotValidException(HttpServletRequest request, MethodArgumentNotValidException e) {
        // 参数错误异常码
        String errorCode = ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode();

        // 获取 BindingResult
        BindingResult bindingResult = e.getBindingResult();

        StringBuilder sb = new StringBuilder();

        // 获取校验不通过的字段，并组合错误信息，格式为： email 邮箱格式不正确, 当前值: '123124qq.com';
        Optional.ofNullable(bindingResult.getFieldErrors()).ifPresent(errors -> {
            errors.forEach(error ->
                    sb.append(error.getField())
                            .append(" ")
                            .append(error.getDefaultMessage())
                            .append(", 当前值: '")
                            .append(error.getRejectedValue())
                            .append("'; ")

            );
        });

        // 错误信息
        String errorMessage = sb.toString();

        log.warn("{} request error, errorCode: {}, errorMessage: {}", request.getRequestURI(), errorCode, errorMessage);

        return Response.fail(errorCode, errorMessage);
    }


    /**
     * 请求方法不支持（例如误发 GET 到仅支持 POST 的接口）。
     * 这通常是前端误调用/浏览器预取/代理探测导致，降级为 WARN 避免刷屏。
     */
    @ExceptionHandler({ HttpRequestMethodNotSupportedException.class })
    @ResponseBody
    public Response<Object> handleMethodNotSupported(HttpServletRequest request, HttpRequestMethodNotSupportedException e) {
        log.warn("{} request method not supported: {}", request.getRequestURI(), e.getMessage());
        return Response.fail(ResponseCodeEnum.PARAM_NOT_VALID.getErrorCode(), "请求方法不支持");
    }

    /**
     * SSE（text/event-stream）场景下，前端主动关闭连接会导致服务端写入时报 IOException。
     * 这属于“客户端中止”，此时响应往往已经是 event-stream，继续返回统一 JSON 会触发二次异常：
     * HttpMessageNotWritableException: No converter ... with preset Content-Type 'text/event-stream'
     *
     * 处理策略：对这类请求仅降级记录，不再向客户端写任何内容。
     */
    @ExceptionHandler({ IOException.class })
    public void handleSseClientAbort(HttpServletRequest request, IOException e) {
        String uri = request.getRequestURI();
        String msg = e.getMessage();

        boolean isSseEndpoint = "/chat/completion".equals(uri) || "/resume-optimize/optimize".equals(uri) || "/ai/generateStream".equals(uri);
        boolean isClientAbort = msg != null && (
                msg.contains("你的主机中的软件中止了一个已建立的连接")
                        || msg.contains("Broken pipe")
                        || msg.contains("Connection reset by peer")
        );

        if (isSseEndpoint && isClientAbort) {
            log.info("{} SSE 客户端已断开连接（忽略异常）：{}", uri, msg);
            return;
        }

        // 非 SSE 客户端断开，按错误处理
        log.error("{} request io error, ", uri, e);
    }

    /**
     * 其他类型异常
     * @param request
     * @param e
     * @return
     */
    @ExceptionHandler({ Exception.class })
    @ResponseBody
    public Response<Object> handleOtherException(HttpServletRequest request, Exception e) {
        log.error("{} request error, ", request.getRequestURI(), e);
        return Response.fail(ResponseCodeEnum.SYSTEM_ERROR);
    }
}
