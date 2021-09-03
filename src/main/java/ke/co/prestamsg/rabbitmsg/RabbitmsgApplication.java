package ke.co.prestamsg.rabbitmsg;

import com.rabbitmq.client.ConnectionFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.PooledChannelConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.Filter;
import java.util.Date;
import org.springframework.amqp.core.Queue;
import java.util.function.Supplier;

@Slf4j
@EnableRabbit
@SpringBootApplication
public class RabbitmsgApplication {

	public static void main(String[] args) {
		SpringApplication.run(RabbitmsgApplication.class, args);
	}


	@Value("${spring.rabbitmq.port}")
	private Integer port;
	@Value("${spring.rabbitmq.addresses}")
	private String host;
	@Value("${spring.rabbitmq.password}")
	private String password;
	@Value("${spring.rabbitmq.username}")
	private String username;
	@Value("${spring.rabbitmq.virtual-host}")
	private String virtualHost;

	/**
	 * RabbitMQ Connection Factory
	 * @return
	 */
	@Bean
	public PooledChannelConnectionFactory pooledChannelConnectionFactory(){
		ConnectionFactory rabbitConnectionFactory = new ConnectionFactory();
		rabbitConnectionFactory.setUsername(username);
		rabbitConnectionFactory.setPassword(password);
		rabbitConnectionFactory.setHost(host);
		rabbitConnectionFactory.setVirtualHost(virtualHost);
		rabbitConnectionFactory.setPort(port);
		return new PooledChannelConnectionFactory(rabbitConnectionFactory);
	}

	/**
	 * RabbitMQ ListenerContainerFactory - We centrally register receipt Post Processors to manage
	 * tenantId binding.
	 *
	 * @return
	 */
	@Bean
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(){
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(pooledChannelConnectionFactory());
		factory.setMessageConverter(jsonMessageConverter());
		factory.setAfterReceivePostProcessors(new MessagePostProcessor() {
			@Override
			public Message postProcessMessage(Message message) throws AmqpException {
				assert tenantIdSupplier().get()==null : "We shouldnt have an active Tenant at this point!";

				String tenantId = message.getMessageProperties().getHeader("tenantId");
				if(tenantId!=null){
					background_thread_db.set(tenantId);
					log.info("Received message with tenantId: "+tenantId + " : Thread "+Thread.currentThread());
				}
				return message;
			}
		});
		return factory;
	}

	/**
	 * RabbitMQ JsonMessageConvertor
	 * @return
	 */
	@Bean
	public MessageConverter jsonMessageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	/**
	 * Queue Declaration - Uses RabbitAdmin to create queue on RabbitMQ
	 * @return
	 */
	@Bean
	public Queue c2bQueue(){
		return new Queue("prestapay.c2b_payment", false);
	}

	/**
	 * RabbitMQ Template for sending messages (May also be used for message retrieval)
	 * @param tenantId
	 * @return
	 */
	@Bean
	RabbitTemplate rabbitTemplate(Supplier<String> tenantId){
		RabbitTemplate template =  new RabbitTemplate(pooledChannelConnectionFactory());
		template.setMessageConverter(jsonMessageConverter());
		template.setBeforePublishPostProcessors(processor -> {
			processor.getMessageProperties().setHeader("tenantId", tenantId.get());
			log.info("RabbitTemplate: Sending message with tenantId: "+tenantId.get()+" : Thread "+Thread.currentThread());
			return processor;
		});
		return template;
	}

	/**
	 * Dummy POJO
	 */
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class C2BData{
		private String transactionId;
		private Date transactionDate;
		private Double amount;
		private String tenantId;
	}

	/**
	 * Rest endpoint for http request
	 */
	@RestController
	@RequestMapping("/api/v1/c2b")
	public class SampleC2BReceiver{

		@Autowired
		private C2BSender sender;

		@PostMapping
		public ResponseEntity receiveC2B(@RequestBody C2BData data){
			sender.sendC2B(data);
			return ResponseEntity.ok().body("Received");
		}
	}

	/**
	 * Sample message sender and receiver service
	 */
	@Service
	public static class C2BSender{
		@Autowired
		private RabbitTemplate template;
		@Autowired
		private Supplier<String> tenantIdSupplier;

		public void sendC2B(C2BData data){
			template.convertAndSend("prestapay.c2b_payment", data);
		}

		@RabbitListener(queues = {"prestapay.c2b_payment"}, containerFactory = "rabbitListenerContainerFactory")
		public void onTransactionReceived(@Payload C2BData data){
			log.info("C2B trx received from rabbit: "+data);
			assert tenantIdSupplier.get()!=null : "TenantID should be been bound at this point";
		}
	}

	/**
	 * A tenantId supplier
	 * @return
	 */
	@Bean
	Supplier<String> tenantIdSupplier(){
		return ()->{
			return background_thread_db.get();
		};
	}

	/**
	 * A web filter to extract tenantId from http request
	 * @return
	 */
	@Bean
	@Order(-101)
	public Filter tenantIdFilter() {
		return (servletRequest, servletResponse, filterChain) -> {
			String tenantId = servletRequest.getParameter("tenantId");
			if(StringUtils.hasLength(tenantId)){
				background_thread_db.set(tenantId);
			}
			try {
				filterChain.doFilter(servletRequest, servletResponse);
			} finally {
				background_thread_db.remove();
			}
		};
	}
	public static final ThreadLocal<String> background_thread_db = new ThreadLocal<>();
}