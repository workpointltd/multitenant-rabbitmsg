package ke.co.prestamsg.rabbitmsg;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RequestReply {

    @RabbitListener(queues = "#{helloQueue}",messageConverter = "simpleMessageConverter")
    public String hello(String name){
        log.info("RECEIVED Request NAME: "+name);
        return "Hello "+name;
    }

}
