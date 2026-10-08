package com.codinglair.taf.runtime.core.reporting;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.util.ClassUtils;

/** Proxies Spring-managed consumer objects that declare neutral TAF reporting annotations. */
public final class ReportingBeanPostProcessor implements BeanPostProcessor {
  private final Supplier<ReportingActionInterceptor> interceptor;

  public ReportingBeanPostProcessor(ReportingActionInterceptor interceptor) {
    this(() -> interceptor);
  }

  public ReportingBeanPostProcessor(Supplier<ReportingActionInterceptor> interceptor) {
    this.interceptor = interceptor;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    Class<?> type = ClassUtils.getUserClass(bean);
    if (MethodIntrospector.selectMethods(type, ReportingActionInterceptor::isReportable)
        .isEmpty()) {
      return bean;
    }
    ProxyFactory proxy = new ProxyFactory(bean);
    proxy.setProxyTargetClass(true);
    proxy.addAdvice((MethodInterceptor) invocation -> invoke(type, invocation));
    return proxy.getProxy(type.getClassLoader());
  }

  private Object invoke(Class<?> type, MethodInvocation invocation) throws Exception {
    Method target = ClassUtils.getMostSpecificMethod(invocation.getMethod(), type);
    return interceptor.get().invoke(target, invocation.getArguments(), () -> proceed(invocation));
  }

  private static Object proceed(MethodInvocation invocation) {
    try {
      return invocation.proceed();
    } catch (Throwable failure) {
      ReportingBeanPostProcessor.<RuntimeException>throwAny(failure);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }
}
