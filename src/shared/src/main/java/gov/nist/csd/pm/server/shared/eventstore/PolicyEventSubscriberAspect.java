/*
package gov.nist.csd.pm.server.shared.eventstore;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PolicyEventSubscriberAspect {


    private final SubscriptionRetryService subscriptionRetryService;

    public PolicyEventSubscriberAspect(SubscriptionRetryService subscriptionRetryService) {
        this.subscriptionRetryService = subscriptionRetryService;
    }

    @Pointcut("execution(* gov.nist.csd.pm.server.shared.eventstore.PolicyEventSubscriber.onCancelled(..))")
    public void onOnCancelled() {
    }

    @AfterReturning("onOnCancelled()")
    public void afterOnCancelled(JoinPoint joinPoint) {
        System.out.println("PolicyEventSubscriber.onCancelled(..)");
        subscriptionRetryService.subscribeWithRetry();
    }
}
*/
