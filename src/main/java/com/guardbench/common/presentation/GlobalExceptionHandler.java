package com.guardbench.common.presentation;

import java.util.ArrayList;
import java.util.List;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.common.presentation.dto.ApiResponse;
import com.guardbench.common.presentation.dto.ErrorDetail;
import com.guardbench.common.presentation.dto.FieldErrorDetail;
import com.guardbench.common.presentation.dto.ValidationErrorDetail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * 예외를 승인된 공통 오류 Envelope로 변환한다.
 *
 * <p>모든 응답의 {@code httpStatus}는 실제 HTTP Status와 같은 값에서 유도한다. 내부 예외, SQL, Provider 응답 원문,
 * Stack Trace와 비밀정보는 응답에 노출하지 않고 서버 로그에만 남긴다.
 *
 * <p>{@link ResponseEntityExceptionHandler}를 상속해 Spring MVC 표준 예외의 Status 판정을 그대로 사용하고,
 * 승인된 Error Code로 표현할 수 있는 예외만 재정의한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String UNKNOWN_FIELD_MESSAGE = "알 수 없는 필드입니다.";
    private static final String INVALID_FORMAT_MESSAGE = "값의 형식이 올바르지 않습니다.";
    private static final String UNREADABLE_BODY_MESSAGE = "요청 본문을 읽을 수 없습니다.";
    private static final String MISSING_VALUE_MESSAGE = "필수 값이 없습니다.";

    /**
     * 요청 계약으로 표현되는 실패를 해당 Code의 HTTP Status로 변환한다.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Object> handleApplicationException(ApplicationException ex) {
        ApplicationErrorCode errorCode = ex.errorCode();
        if (errorCode == ApplicationErrorCode.INTERNAL_SERVER_ERROR) {
            log.error("서버 내부 오류로 요청을 완료하지 못했습니다.", ex);
        } else if (log.isDebugEnabled()) {
            log.debug("Application Error로 요청을 종료했습니다. code={}", errorCode.code(), ex);
        }
        HttpStatus status = HttpStatus.valueOf(errorCode.httpStatus());
        return errorResponse(status, ex.getMessage(), ErrorDetail.of(errorCode));
    }

    /**
     * 예상하지 못한 동기 서버 처리 실패를 노출 가능한 형태로만 변환한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception ex) {
        log.error("처리하지 못한 예외가 발생했습니다.", ex);
        ApplicationErrorCode errorCode = ApplicationErrorCode.INTERNAL_SERVER_ERROR;
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR, errorCode.defaultMessage(), ErrorDetail.of(errorCode));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        addBindingErrors(errors, ex.getBindingResult().getFieldErrors(), ex.getBindingResult().getGlobalErrors());
        return validationResponse(errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<FieldErrorDetail> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                addBindingErrors(errors, parameterErrors.getFieldErrors(), parameterErrors.getGlobalErrors());
                continue;
            }
            String field = parameterField(result);
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                errors.add(new FieldErrorDetail(field, error.getDefaultMessage()));
            }
        }
        for (MessageSourceResolvable error : ex.getCrossParameterValidationResults()) {
            errors.add(FieldErrorDetail.ofRequest(error.getDefaultMessage()));
        }
        return validationResponse(errors);
    }

    /**
     * 알 수 없는 Request Body 필드, 잘못된 값 형식, 읽을 수 없는 Body를 모두 Validation 오류로 변환한다.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validationResponse(List.of(bodyFieldError(ex)));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = FieldErrorDetail.REQUEST_FIELD;
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            field = mismatch.getName();
        } else if (ex.getPropertyName() != null) {
            field = ex.getPropertyName();
        }
        return validationResponse(List.of(new FieldErrorDetail(field, INVALID_FORMAT_MESSAGE)));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validationResponse(
                List.of(new FieldErrorDetail(ex.getParameterName(), MISSING_VALUE_MESSAGE)));
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(
            MissingPathVariableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return validationResponse(List.of(new FieldErrorDetail(ex.getVariableName(), MISSING_VALUE_MESSAGE)));
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String field = ex instanceof MissingRequestHeaderException missingHeader
                ? missingHeader.getHeaderName()
                : FieldErrorDetail.REQUEST_FIELD;
        return validationResponse(List.of(new FieldErrorDetail(field, MISSING_VALUE_MESSAGE)));
    }

    private void addBindingErrors(
            List<FieldErrorDetail> errors, List<FieldError> fieldErrors, List<ObjectError> globalErrors) {
        for (FieldError fieldError : fieldErrors) {
            errors.add(new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        for (ObjectError globalError : globalErrors) {
            errors.add(FieldErrorDetail.ofRequest(globalError.getDefaultMessage()));
        }
    }

    private FieldErrorDetail bodyFieldError(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof UnrecognizedPropertyException unrecognized) {
            return new FieldErrorDetail(
                    jsonField(unrecognized, unrecognized.getPropertyName()), UNKNOWN_FIELD_MESSAGE);
        }
        if (cause instanceof MismatchedInputException mismatched) {
            return new FieldErrorDetail(jsonField(mismatched, null), INVALID_FORMAT_MESSAGE);
        }
        return FieldErrorDetail.ofRequest(UNREADABLE_BODY_MESSAGE);
    }

    /**
     * Jackson의 참조 경로를 {@code testCases[0].name} 형식의 외부 API 필드 이름으로 변환한다.
     */
    private String jsonField(JacksonException ex, String fallbackProperty) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : ex.getPath()) {
            String propertyName = reference.getPropertyName();
            if (propertyName != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(propertyName);
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        if (!path.isEmpty()) {
            return path.toString();
        }
        return fallbackProperty != null ? fallbackProperty : FieldErrorDetail.REQUEST_FIELD;
    }

    /**
     * Path·Query·Header 파라미터의 외부 API 이름을 찾고, 반복 Query는 {@code sort[1]} 형식으로 표현한다.
     */
    private String parameterField(ParameterValidationResult result) {
        String name = externalParameterName(result.getMethodParameter());
        Integer containerIndex = result.getContainerIndex();
        return containerIndex != null ? name + "[" + containerIndex + "]" : name;
    }

    private String externalParameterName(MethodParameter parameter) {
        RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
        if (requestParam != null) {
            String declared = firstNonEmpty(requestParam.name(), requestParam.value());
            if (declared != null) {
                return declared;
            }
        }
        PathVariable pathVariable = parameter.getParameterAnnotation(PathVariable.class);
        if (pathVariable != null) {
            String declared = firstNonEmpty(pathVariable.name(), pathVariable.value());
            if (declared != null) {
                return declared;
            }
        }
        RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null) {
            String declared = firstNonEmpty(requestHeader.name(), requestHeader.value());
            if (declared != null) {
                return declared;
            }
        }
        String parameterName = parameter.getParameterName();
        return parameterName != null ? parameterName : FieldErrorDetail.REQUEST_FIELD;
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private ResponseEntity<Object> validationResponse(List<FieldErrorDetail> errors) {
        ApplicationErrorCode errorCode = ApplicationErrorCode.VALIDATION_ERROR;
        List<FieldErrorDetail> resolved = errors.isEmpty()
                ? List.of(FieldErrorDetail.ofRequest(errorCode.defaultMessage()))
                : errors;
        return errorResponse(
                HttpStatus.valueOf(errorCode.httpStatus()),
                errorCode.defaultMessage(),
                ValidationErrorDetail.of(resolved));
    }

    private ResponseEntity<Object> errorResponse(HttpStatus status, String message, Object data) {
        return ResponseEntity.status(status).body(ApiResponse.of(status, message, data));
    }
}
