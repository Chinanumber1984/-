package vip.xiaonuo.srm.workspace;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import vip.xiaonuo.common.pojo.CommonResult;
@RestControllerAdvice(assignableTypes=SrmWorkspaceController.class)
public class SrmInputErrors {
 @ExceptionHandler(MethodArgumentNotValidException.class) public CommonResult<?> invalid(MethodArgumentNotValidException ex){return CommonResult.get(400,"字段校验失败："+ex.getBindingResult().getFieldErrors().stream().map(e->e.getField()).distinct().toList(),null);}
 @ExceptionHandler(HttpMessageNotReadableException.class) public CommonResult<?> unreadable(){return CommonResult.get(400,"请求格式或字段类型不正确",null);}
}
