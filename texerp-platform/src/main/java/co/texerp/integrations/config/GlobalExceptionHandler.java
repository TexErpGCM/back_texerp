package co.texerp.integrations.config;

import co.texerp.integrations.dto.ApiResponse;
import co.texerp.integrations.exception.InvalidRefreshTokenException;
import co.texerp.integrations.exception.UserConflictException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
            IllegalArgumentException.class,
            NoSuchElementException.class
    })
    public ResponseEntity<ApiResponse<Void>> badRequest(
            RuntimeException exception
    ) {

        String message =
                exception.getMessage() == null
                        || exception.getMessage().isBlank()
                        ? "La solicitud contiene información inválida"
                        : exception.getMessage();

        return ResponseEntity
                .badRequest()
                .body(
                        ApiResponse.error(message)
                );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(
            MethodArgumentNotValidException exception
    ) {

        String message =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .findFirst()
                        .map(error ->
                                error.getDefaultMessage() == null
                                        ? "Campo inválido"
                                        : error.getDefaultMessage()
                        )
                        .orElse(
                                "La solicitud contiene información inválida"
                        );

        return ResponseEntity
                .badRequest()
                .body(
                        ApiResponse.error(message)
                );
    }

    @ExceptionHandler(UserConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserConflict(
            UserConflictException exception
    ) {

        String message =
                "Conflicto en el campo '"
                        + exception.getField()
                        + "': "
                        + exception.getMessage();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.error(message)
                );
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(
            BusinessConflictException exception
    ) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.error(
                                exception.getMessage()
                        )
                );
    }


    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> integrity(
            DataIntegrityViolationException exception
    ) {

        LOGGER.warn(
                "Conflicto de integridad de datos",
                exception
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(
                        ApiResponse.error(
                                "La operación no puede completarse porque el registro tiene información relacionada"
                        )
                );
    }


    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(
            EntityNotFoundException exception
    ) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(
                        ApiResponse.error(
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException exception
    ) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiResponse.error(
                                "Credenciales inválidas"
                        )
                );
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabledUser(
            DisabledException exception
    ) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiResponse.error(
                                "Credenciales inválidas"
                        )
                );
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshToken(
            InvalidRefreshTokenException exception
    ) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiResponse.error(
                                "Refresh token inválido o expirado"
                        )
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> internalError(
            Exception exception
    ) {

        LOGGER.error(
                "Error interno no controlado",
                exception
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiResponse.error(
                                "Ocurrió un error interno en el servidor"
                        )
                );
    }
}