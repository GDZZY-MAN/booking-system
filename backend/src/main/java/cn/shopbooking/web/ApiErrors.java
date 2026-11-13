package cn.shopbooking.web;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    public record ErrorBody(String code, String message, String requestId) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> business(ApiException error) {
        return ResponseEntity.status(error.status())
                .body(new ErrorBody(error.code(), error.getMessage(), MDC.get("requestId")));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorBody> malformed(Exception error) {
        return business(ApiException.invalid("请求格式有误，请检查后重试。"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorBody> missing(NoResourceFoundException error) {
        return business(ApiException.missing("这个地址不存在。"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorBody> database(DataAccessException error) {
        Throwable cause = error.getMostSpecificCause();
        String state = cause instanceof SQLException sql ? sql.getSQLState() : null;
        if ("23P01".equals(state)) {
            return business(ApiException.conflict("SLOT_UNAVAILABLE", "这个时段刚被预约，请重新选择。"));
        }
        if ("23505".equals(state)) {
            return business(ApiException.conflict("ALREADY_EXISTS", "这个账号或记录已存在，请检查后重试。"));
        }
        log.warn("Database request failed: {} ({})", error.getClass().getSimpleName(), state);
        return unavailable();
    }

    @ExceptionHandler({CannotCreateTransactionException.class, TransactionSystemException.class})
    public ResponseEntity<ErrorBody> transaction(Exception error) {
        log.warn("Database transaction failed: {}", error.getClass().getSimpleName());
        return unavailable();
    }

    private ResponseEntity<ErrorBody> unavailable() {
        return business(new ApiException(503, "TEMPORARILY_UNAVAILABLE", "服务暂时不可用，请稍后重试。"));
    }
}
