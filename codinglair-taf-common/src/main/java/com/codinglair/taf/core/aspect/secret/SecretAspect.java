package com.codinglair.taf.core.aspect.secret;

import com.codinglair.taf.core.test.abstraction.BaseTest;
import com.codinglair.taf.core.utils.secret.abstraction.SecretManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class SecretAspect {

    @Around("@annotation(secret.annotation.com.codinglair.taf.core.Decrypt)")
    //@Around("execution(* com.taf..*.get*()) && @annotation(com.taf.common.annotations.Decrypt)")
    public Object handleDecryption(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();
        if (!(result instanceof String)) return result;

        String value = (String) result;
        // Ask the specific strategy if it recognizes this format
        SecretManager manager = BaseTest.getSecretManager();

        if (manager.isEncrypted(value)) {
            return manager.decrypt(value);
        }

        return value;
    }
}