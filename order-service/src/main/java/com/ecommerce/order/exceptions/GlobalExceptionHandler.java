package com.ecommerce.order.exceptions;

import com.ecommerce.order.models.dtos.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> methodNotAllowed(
      HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ApiErrorResponse.of("METHOD_NOT_ALLOWED", "Phuong thuc HTTP khong duoc ho tro cho duong dan nay", request.getRequestId()));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> validation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<String> errors = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.validation(errors, request.getRequestId()));
  }

  @ExceptionHandler(OrderNotFoundException.class)
  ResponseEntity<ApiErrorResponse> notFound(
      OrderNotFoundException exception, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception, request);
  }

  @ExceptionHandler(IdempotencyKeyConflictException.class)
  ResponseEntity<ApiErrorResponse> idempotencyConflict(
      IdempotencyKeyConflictException exception, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception, request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> badRequest(
      IllegalArgumentException exception, HttpServletRequest request) {
    String value = exception.getMessage();
    String code = value != null && value.matches("[A-Z][A-Z0-9_]+") ? value : "INVALID_REQUEST";
    HttpStatus status = code.endsWith("NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(code, argumentMessage(code, value), request.getRequestId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiErrorResponse> conflict(
      IllegalStateException exception, HttpServletRequest request) {
    String value = exception.getMessage();
    String code = switch (value == null ? "" : value) {
      case "VERSION_CONFLICT", "CART_CHANGED", "SKU_NOT_SELLABLE", "ACCOUNT_NOT_ACTIVE",
          "INVALID_STATE_TRANSITION", "INVALID_RESERVATION_STATE", "INVALID_INVENTORY_RESERVATION",
          "RESERVATION_ALREADY_RELEASED", "RESERVATION_ALREADY_COMMITTED", "OUTBOX_NOT_FAILED" -> value;
      default -> "INVALID_STATE";
    };
    String message = switch (code) {
      case "VERSION_CONFLICT" -> "Dữ liệu đã thay đổi, vui lòng tải lại và thử lại";
      case "CART_CHANGED" -> "Giỏ hàng đã thay đổi, vui lòng kiểm tra lại giỏ hàng";
      case "SKU_NOT_SELLABLE" -> "SKU, sản phẩm hoặc danh mục không còn được bán";
      case "ACCOUNT_NOT_ACTIVE" -> "Tài khoản chưa được kích hoạt";
      case "INVALID_STATE_TRANSITION" -> "Trạng thái đơn hàng không cho phép thao tác này";
      case "INVALID_RESERVATION_STATE", "INVALID_INVENTORY_RESERVATION" -> "Trạng thái giữ hàng không hợp lệ";
      case "RESERVATION_ALREADY_RELEASED" -> "Hàng đã được giải phóng trước đó";
      case "RESERVATION_ALREADY_COMMITTED" -> "Hàng đã được xác nhận trước đó";
      case "OUTBOX_NOT_FAILED" -> "Sự kiện không ở trạng thái cần xử lý lại";
      default -> "Trạng thái yêu cầu không hợp lệ";
    };
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of(code, message, request.getRequestId()));
  }

  @ExceptionHandler(PriceChangedException.class)
  ResponseEntity<ApiErrorResponse> priceChanged(
      PriceChangedException exception, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "PRICE_CHANGED", exception, request);
  }

  @ExceptionHandler(CatalogUnavailableException.class)
  ResponseEntity<ApiErrorResponse> catalogUnavailable(
      CatalogUnavailableException exception, HttpServletRequest request) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", exception, request);
  }

  @ExceptionHandler(IdentityUnavailableException.class)
  ResponseEntity<ApiErrorResponse> identityUnavailable(
      IdentityUnavailableException exception, HttpServletRequest request) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE", exception, request);
  }

  private ResponseEntity<ApiErrorResponse> error(
      HttpStatus status, String code, Exception exception, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(code, exception.getMessage(), request.getRequestId()));
  }

  private String argumentMessage(String code, String rawMessage) {
    return switch (code) {
      case "SKU_NOT_FOUND" -> "Không tìm thấy SKU";
      case "ADDRESS_NOT_FOUND" -> "Không tìm thấy địa chỉ";
      case "CART_EMPTY" -> "Giỏ hàng đang trống";
      case "SKU_ID_MISMATCH" -> "SKU trong yêu cầu không khớp với dữ liệu giỏ hàng";
      case "IDEMPOTENCY_KEY_REQUIRED" -> "Idempotency-Key là bắt buộc";
      case "EVENT_ID_REQUIRED" -> "Thiếu mã sự kiện";
      case "INVALID_PAYMENT_ID" -> "Mã thanh toán không hợp lệ";
      case "INVALID_ORDER_ID", "ORDER_ID_REQUIRED" -> "Mã đơn hàng không hợp lệ";
      case "VERSION_REQUIRED" -> "Thiếu phiên bản dữ liệu";
      case "INVALID_TIME_RANGE" -> "Khoảng thời gian không hợp lệ";
      case "INVALID_RESERVATION_QUANTITY" -> "Số lượng giữ hàng không hợp lệ";
      case "CORRELATION_ID_INVALID" -> "Mã tương quan không hợp lệ";
      case "ORDER_EVENT_ENVELOPE_INVALID", "PAYMENT_EVENT_ENVELOPE_INVALID" -> "Định dạng sự kiện không hợp lệ";
      default -> rawMessage != null && !rawMessage.matches("[A-Z][A-Z0-9_]+")
          ? rawMessage : "Dữ liệu yêu cầu không hợp lệ";
    };
  }
}
