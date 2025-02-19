package com.github.tomato.core;

import com.github.tomato.annotation.Repeat;
import com.github.tomato.constant.TomatoConstant;
import com.github.tomato.exception.ElSyntaxException;
import com.github.tomato.exception.RepeatOptException;
import com.github.tomato.support.CachedClassResponseData;
import com.github.tomato.support.RepeatToInterceptSupport;
import com.github.tomato.support.TokenProviderSupport;
import com.github.tomato.util.StaticContext;
import com.google.gson.Gson;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

/**
 * 拦截器拦截被幂等的接口
 * 优化逻辑,解决方法执行耗时,导致幂等拦截失效。
 *
 * @author liuxin
 */
@Aspect
public class TomatoV2Interceptor {
    private Gson mGson = new Gson();

    private Idempotent idempotent;

    private TokenProviderSupport tokenProviderSupport;

    private RepeatToInterceptSupport interceptSupport;

    public TomatoV2Interceptor() {
    }

    public TomatoV2Interceptor(Idempotent idempotent, TokenProviderSupport tokenProviderSupport, RepeatToInterceptSupport interceptSupport) {
        this.idempotent = idempotent;
        this.tokenProviderSupport = tokenProviderSupport;
        this.interceptSupport = interceptSupport;
    }

    private String methodLockTomatoToken(Method method, String tomatoToken) {
        return tomatoToken + method.getName();
    }

    @Around("@annotation(com.github.tomato.annotation.Repeat)")
    public Object doAround(ProceedingJoinPoint pjp) {
        //1. 获取唯一键的获取方式
        Object[] args = pjp.getArgs();
        Method method = findMethod(pjp.getSignature());
        Repeat repeat = AnnotationUtils.getAnnotation(method, Repeat.class);
        Object result;
        Exception e;
        String tomatoToken = "";
        try {
            //2. 获取唯一键
            tomatoToken = tokenProviderSupport.findTomatoToken(method, args);
            // 如果为空直接报错
            if (StringUtils.isEmpty(tomatoToken) || Objects.isNull(repeat)) {
                throw new ElSyntaxException("el语法错误:[" + Arrays.asList(args) + "]");
            }
            //3. 唯一键键不存在,直接执行
            String methodLockToken = methodLockTomatoToken(method, tomatoToken);
            String responseLockToken = "TMT_" + methodLockToken + "_response";
            String response = idempotent.getIdempotent(responseLockToken);
            if (idempotent(methodLockToken, TomatoConstant.DEFAULT_VALUE, repeat.methodLock(), repeat) && response == null) {
                StaticContext.setToken(tomatoToken);
                result = pjp.proceed();
                CachedClassResponseData cachedClassResponseData = new CachedClassResponseData(result.getClass().getName(), mGson.toJson(result));
                idempotent(responseLockToken, mGson.toJson(cachedClassResponseData), repeat.value(), repeat);
            } else {
                idempotent(responseLockToken, response, repeat.value(), repeat);
                //防重之后交给用户来处理
                Object proceed = interceptSupport.proceed(method, args, response, repeat.useLastResponse());
                if (proceed == null) {
                    Class<? extends Exception> throwable = repeat.throwable();
                    Constructor<? extends Exception> declaredConstructor = throwable.getDeclaredConstructor(String.class);
                    String message = repeat.message();
                    e = declaredConstructor.newInstance(message);
                    throw e;
                }
                result = proceed;
            }
        } catch (RepeatOptException rop) {
            throw rop;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            throw new RuntimeException(throwable);
        } finally {
            StaticContext.clear();
            if (!StringUtils.isEmpty(tomatoToken)) {
                // 移除本次方法锁
                idempotent.delIdempotent(methodLockTomatoToken(method, tomatoToken));
            }
        }
        return result;
    }

    public boolean idempotent(String tomatoToken, String value, Long millisecond, Repeat repeat) {
        RepeatTypeEnum typeEnum = repeat.type();
        if (RepeatTypeEnum.FIXED_WINDOW == typeEnum) {
            return idempotent.fixedIdempotent(tomatoToken, value, millisecond);
        } else if (RepeatTypeEnum.SLIDING_WINDOW == typeEnum) {
            return idempotent.idempotent(tomatoToken, value, millisecond);
        }
        return idempotent.idempotent(tomatoToken, value, millisecond);
    }

    private Method findMethod(Signature signature) {
        MethodSignature ms = (MethodSignature) signature;
        return ms.getMethod();
    }

}
