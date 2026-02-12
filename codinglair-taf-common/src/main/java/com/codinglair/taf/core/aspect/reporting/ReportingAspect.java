package com.codinglair.taf.core.aspect.reporting;

import com.codinglair.taf.core.annotation.reporting.CaptureOutput;
import com.codinglair.taf.core.annotation.reporting.TafDescription;
import com.codinglair.taf.core.annotation.reporting.TafStep;
import com.codinglair.taf.core.data.abstraction.TestContext;
import com.codinglair.taf.core.test.abstraction.BaseTest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

@Aspect
public class ReportingAspect {

    @Around("execution(* *(..)) && @annotation(tafStep)")
    public Object handleStep(ProceedingJoinPoint joinPoint, TafStep tafStep) throws Throwable {
        // 'tafStep.value()' is an instance method call on the annotation found at the join point
        String rawValue = tafStep.value();
        String resolved = resolvePlaceholders(rawValue, joinPoint);

        BaseTest.getReporter().logStep(resolved);
        return joinPoint.proceed();
    }

    @Around("@annotation(tafDescription)")
    public Object handleStep(ProceedingJoinPoint joinPoint, TafDescription tafDescription) throws Throwable {
        String rawValue = tafDescription.value();
        String resolved = resolvePlaceholders(rawValue, joinPoint);

        BaseTest.getReporter().setDescription(resolved);
        return joinPoint.proceed();
    }

    @Around("@annotation(captureOutput)")
    public Object handleCapture(ProceedingJoinPoint joinPoint, CaptureOutput captureOutput) throws Throwable {
        Object result = joinPoint.proceed();

        if (result != null) {
            // Use your BaseTest thread-locals to route the data
            TestContext<?, Object> context = (TestContext<?, Object>) BaseTest.getContext();
            String testCaseId = BaseTest.getActiveTestCaseId();

            context.recordActual(testCaseId, result);
        }
        return result;
    }

    private String resolvePlaceholders(String template, ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();

        String resolved = template;
        for (int i = 0; i < args.length; i++) {
            // Replace positional placeholders like {0}, {1}
            resolved = resolved.replace("{" + i + "}", String.valueOf(args[i]));
            // Replace named placeholders like {url}
            if (parameterNames != null) {
                resolved = resolved.replace("{" + parameterNames[i] + "}", String.valueOf(args[i]));
            }
        }
        return resolved;
    }
}
