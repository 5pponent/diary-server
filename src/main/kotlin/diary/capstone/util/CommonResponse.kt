package diary.capstone.util

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

data class BoolResponse(val result: Boolean = true)

data class ErrorResponse(val cause: String = "", val message: String = "")

data class ErrorListResponse(val errors: List<ErrorResponse>)

fun badRequest(ex: Exception) =
    ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse(ex.javaClass.simpleName, ex.message!!))

fun badRequest(body: Any) =
    ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(body)

fun unauthorized(ex: Exception) =
    ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(ErrorResponse(ex.javaClass.simpleName, ex.message!!))
