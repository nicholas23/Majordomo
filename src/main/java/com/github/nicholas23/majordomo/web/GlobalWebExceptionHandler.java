/**
 * 目的：Web 控制器全局異常處理器
 * 關鍵項目：
 * 1. 攔截 Controller 拋出的 NoSuchElementException、IllegalArgumentException 與一般例外
 * 2. 依據是否為 HTMX 請求回傳友好的 Alert 片段或錯誤訊息
 * 模組：web
 */
package com.github.nicholas23.majordomo.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalWebExceptionHandler.class);

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e, HttpServletRequest request) {
        log.warn("[GlobalWebExceptionHandler] 資源不存在: uri={}, error={}", request.getRequestURI(), e.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "找不到指定的資源: " + e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("[GlobalWebExceptionHandler] 無效請求參數: uri={}, error={}", request.getRequestURI(), e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "請求參數錯誤: " + e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralException(Exception e, HttpServletRequest request) {
        log.error("[GlobalWebExceptionHandler] 系統未處理異常: uri={}", request.getRequestURI(), e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "系統處理發生錯誤: " + e.getMessage(), request);
    }

    private ResponseEntity<String> buildErrorResponse(HttpStatus status, String message, HttpServletRequest request) {
        boolean isHtmx = "true".equalsIgnoreCase(request.getHeader("HX-Request"));
        if (isHtmx) {
            String htmlAlert = String.format(
                    "<div class=\"alert alert-danger m-3 d-flex align-items-center justify-content-between\" role=\"alert\">"
                            + "<div><i class=\"bi bi-exclamation-triangle-fill me-2\"></i>%s</div>"
                            + "<button type=\"button\" class=\"btn-close\" data-bs-dismiss=\"alert\" aria-label=\"Close\"></button>"
                            + "</div>",
                    message
            );
            return ResponseEntity.status(status).body(htmlAlert);
        }
        return ResponseEntity.status(status).body(message);
    }
}

/* ### Review Checklist ###
 * 1. 異常涵蓋：包含 NotFound、BadRequest 與一般 Exception？ ✓
 * 2. HTMX 友善：辨識 HX-Request 並返回對應 UI 片段？ ✓
 * 3. 日誌：記錄發生異常的 URI 與錯誤詳情？ ✓
 */
