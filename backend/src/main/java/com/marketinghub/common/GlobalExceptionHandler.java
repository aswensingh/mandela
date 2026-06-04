package com.marketinghub.common;

import com.marketinghub.auth.UsernameAlreadyUsedException;
import com.marketinghub.auth.InvalidRefreshTokenException;
import com.marketinghub.campaign.CampaignNotFoundException;
import com.marketinghub.campaign.InvalidCampaignStateException;
import com.marketinghub.conversation.ConversationNotFoundException;
import com.marketinghub.conversation.InvalidConversationStateException;
import com.marketinghub.knowledge.KnowledgeDocumentNotFoundException;
import com.marketinghub.customer.CustomerNotFoundException;
import com.marketinghub.customer.DuplicatePhoneException;
import com.marketinghub.customer.importjob.ImportJobNotFoundException;
import com.marketinghub.template.DuplicateTemplateException;
import com.marketinghub.template.TemplateNotFoundException;
import com.marketinghub.tenant.TenantNotFoundException;
import com.marketinghub.user.InvalidRoleException;
import com.marketinghub.user.UserNotFoundException;
import com.marketinghub.whatsapp.WhatsAppApiException;
import com.marketinghub.whatsapp.WhatsAppNotConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("VALIDATION_FAILED", message, traceId()));
    }

    @ExceptionHandler(UsernameAlreadyUsedException.class)
    public ResponseEntity<ApiError> handleUsernameUsed(UsernameAlreadyUsedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("USERNAME_ALREADY_USED", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleInvalidRefresh(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.of("INVALID_REFRESH_TOKEN", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiError.of("UNAUTHENTICATED", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiError.of("ACCESS_DENIED", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("NOT_FOUND", "No such endpoint: " + ex.getResourcePath(), traceId()));
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ApiError> handleTenantNotFound(TenantNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("TENANT_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("USER_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(InvalidRoleException.class)
    public ResponseEntity<ApiError> handleInvalidRole(InvalidRoleException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("INVALID_ROLE", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiError> handleCustomerNotFound(CustomerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("CUSTOMER_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    public ResponseEntity<ApiError> handleDuplicatePhone(DuplicatePhoneException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("DUPLICATE_PHONE", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(ImportJobNotFoundException.class)
    public ResponseEntity<ApiError> handleImportJobNotFound(ImportJobNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("IMPORT_JOB_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<ApiError> handleTemplateNotFound(TemplateNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("TEMPLATE_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(DuplicateTemplateException.class)
    public ResponseEntity<ApiError> handleDuplicateTemplate(DuplicateTemplateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("DUPLICATE_TEMPLATE", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(CampaignNotFoundException.class)
    public ResponseEntity<ApiError> handleCampaignNotFound(CampaignNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("CAMPAIGN_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(InvalidCampaignStateException.class)
    public ResponseEntity<ApiError> handleInvalidCampaignState(InvalidCampaignStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("INVALID_CAMPAIGN_STATE", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ApiError> handleConversationNotFound(ConversationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("CONVERSATION_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(KnowledgeDocumentNotFoundException.class)
    public ResponseEntity<ApiError> handleDocNotFound(KnowledgeDocumentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of("DOCUMENT_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(InvalidConversationStateException.class)
    public ResponseEntity<ApiError> handleInvalidConvoState(InvalidConversationStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of("INVALID_CONVERSATION_STATE", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("BAD_REQUEST", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(WhatsAppNotConfiguredException.class)
    public ResponseEntity<ApiError> handleWhatsAppNotConfigured(WhatsAppNotConfiguredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiError.of("WHATSAPP_NOT_CONFIGURED", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(WhatsAppApiException.class)
    public ResponseEntity<ApiError> handleWhatsAppApi(WhatsAppApiException ex) {
        // Meta upstream failure surfaces as 502 Bad Gateway — the DB row already records FAILED.
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ApiError.of("WHATSAPP_API_ERROR", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ApiError.of("METHOD_NOT_ALLOWED", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleFallback(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of("INTERNAL_ERROR", "Internal server error", traceId()));
    }

    private String traceId() {
        String id = MDC.get(RequestTraceIdFilter.MDC_KEY);
        return id == null ? "unknown" : id;
    }
}
