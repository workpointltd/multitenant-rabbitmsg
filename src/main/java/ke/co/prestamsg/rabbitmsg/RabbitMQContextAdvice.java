package ke.co.prestamsg.rabbitmsg;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Aspect
public class RabbitMQContextAdvice {

    @Pointcut("@annotation(org.springframework.amqp.rabbit.annotation.RabbitListener)")
    public void rabbitListenerMethods(){}

    @After("rabbitListenerMethods()")
    public void clearThreadLocalVariables(JoinPoint joinPoint){
        String methodName = joinPoint.getSignature().getName();
        RabbitmsgApplication.background_thread_db.remove();
        log.info("AFTER RABBIT_LISTENER ON - "+methodName+", cleared Thread Local Vars");
    }

}
