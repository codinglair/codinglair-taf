package com.codinglair.taf.runtime.core.reporting;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
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
    proxy.addAdvice(
        (MethodInterceptor)
            invocation -> {
              Method target = ClassUtils.getMostSpecificMethod(invocation.getMethod(), type);
              try {
                return interceptor
                    .get()
                    .invoke(
                        target,
                        invocation.getArguments(),
                        () -> {
                          try {
                            return invocation.proceed();
                          } catch (Throwable failure) {
                            ReportingBeanPostProcessor.<RuntimeException>throwAny(failure);
                            return null;
                          }
                        });
              } catch (Exception failure) {
                throw failure;
              }
            });
    return proxy.getProxy(type.getClassLoader());
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void throwAny(Throwable failure) throws E {
    throw (E) failure;
  }
}
