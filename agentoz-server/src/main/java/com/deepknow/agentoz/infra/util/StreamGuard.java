package com.deepknow.agentoz.infra.util;

import com.deepknow.agentoz.api.common.exception.AgentOzErrorCode;
import com.deepknow.agentoz.api.common.exception.AgentOzException;
import com.deepknow.agentoz.api.dto.TaskResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;

import java.util.function.Consumer;

/**
 * 流式调用安全防护工具
 * <p>
 * 确保 StreamObserver 在任何情况下都能正确关闭，防止客户端挂起。
 * </p>
 */
@Slf4j
public class StreamGuard {

    /**
     * 安全执行业务逻辑，自动处理异常和流关闭
     *
     * @param observer 响应流观察者
     * @param logic    业务逻辑闭包
     * @param traceInfo 用于日志的追踪信息
     */
    public static void run(StreamObserver<TaskResponse> observer, Runnable logic, String traceInfo) {
        try {
            logic.run();
            // 注意：不要在这里调用 onCompleted，因为如果是异步流，业务逻辑可能还没结束。
            // 这里的 run 仅保护同步启动阶段的异常。
        } catch (AgentOzException e) {
            log.error("业务异常 [{}]: {}", traceInfo, e.getMessage());
            sendError(observer, e.getErrorCode().getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("参数非法 [{}]: {}", traceInfo, e.getMessage());
            sendError(observer, AgentOzErrorCode.INVALID_PARAM.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("系统未捕获异常 [{}]: {}", traceInfo, e.getMessage(), e);
            sendError(observer, AgentOzErrorCode.SYSTEM_ERROR.getCode(), "系统内部错误: " + e.getMessage());
        }
    }

    /**
     * 发送错误响应并关闭流
     */
    public static void sendError(StreamObserver<TaskResponse> observer, String code, String msg) {
        try {
            TaskResponse resp = new TaskResponse();
            resp.setStatus("ERROR");
            resp.setErrorMessage(String.format("[%s] %s", code, msg));
            observer.onNext(resp);
            observer.onCompleted();
        } catch (Exception ex) {
            log.error("发送错误响应失败 (流可能已关闭)", ex);
        }
    }
    
    /**
     * 创建一个带异常防护的透传 Observer
     */
    public static <T> StreamObserver<T> wrapObserver(
            StreamObserver<TaskResponse> downstream,
            Consumer<T> onNextLogic,
            String traceInfo
    ) {
        return new org.apache.dubbo.common.stream.StreamObserver<T>() {
            @Override
            public void onNext(T data) {
                try {
                    onNextLogic.accept(data);
                } catch (Exception e) {
                    log.error("流式数据处理异常 [{}]: {}", traceInfo, e.getMessage(), e);
                    // 决定是否中断流？通常数据转换错误不应中断整个流，除非致命
                    // 这里选择记录日志，或可选择调用 onError
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("上游流异常 [{}]: {}", traceInfo, t.getMessage());
                // 不要立即调用 onCompleted，而是传递错误给下游
                downstream.onError(t);
            }

            @Override
            public void onCompleted() {
                log.info("上游流结束 [{}]", traceInfo);
                downstream.onCompleted();
            }
        };
    }
}
