package lx.edu.myabweb.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LogAdvice {

	//@Before("bean(*)")
	@Before("execution(* lx.edu.myabweb.controller.Ab*.*(..))")
	public void beforeTarget(JoinPoint jp) {
		String className = jp.getTarget().getClass().getName();
		String methodName = jp.getSignature().getName();
		System.out.printf("LogAdvice.beforeTarget(). %s.%s()\n", className, methodName);
	}
}
