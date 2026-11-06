package com.drone.rental.common.exception;
import com.drone.rental.common.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class GlobalExceptionHandler {
 public static int status(int code) {
  if(code>=400 && code<=599)return code;
  return switch(code){case 1002->401;case 1003,1005->403;case 1001,1101,1201,1301,1401,1501,1601,1701,1801->404;case 1901,1902->400;default->409;};
 }
 @ExceptionHandler(BusinessException.class) public ResponseEntity<Result<Void>> business(BusinessException e){return ResponseEntity.status(status(e.getCode())).body(Result.error(e.getCode(),e.getMessage()));}
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,jakarta.validation.ConstraintViolationException.class})
 public ResponseEntity<Result<Void>> bad(Exception e){return ResponseEntity.badRequest().body(Result.error(400,"请求参数不符合要求"));}
 @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<Result<Void>> conflict(Exception e){return ResponseEntity.status(409).body(Result.error(409,"数据约束冲突"));}
 @ExceptionHandler(NoResourceFoundException.class) public ResponseEntity<Result<Void>> absent(Exception e){return ResponseEntity.status(404).body(Result.error(404,"资源不存在"));}
 @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class) public ResponseEntity<Result<Void>> method(Exception e){return ResponseEntity.status(405).body(Result.error(405,"方法不允许"));}
 @ExceptionHandler(Exception.class) public ResponseEntity<Result<Void>> unexpected(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).error("Unexpected failure type={}",e.getClass().getName());return ResponseEntity.internalServerError().body(Result.error(500,"内部错误"));}
}
