package ke.co.prestamsg.rabbitmsg;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Date;
import java.util.Random;

@SpringBootTest(classes = RabbitmsgApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SpringBootConfiguration
@ExtendWith(SpringExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
class RabbitmsgApplicationTests {

	@Autowired
	RabbitmsgApplication.C2BSender sender;

	@LocalServerPort
	private int port;

	@Autowired
	TestRestTemplate restTemplate;

	HttpHeaders headers = new HttpHeaders();

	@Test
	public void sendTransaction(){
		String tenantId = "t12601";

		{
			String transactionId = "TR" + new Random().nextInt(10000) + "";
			RabbitmsgApplication.C2BData data = new RabbitmsgApplication.C2BData(transactionId, new Date(), 100.00, tenantId);
			String url = createURLWithPort("/api/v1/c2b?tenantId={tenantId}");
			ResponseEntity response = restTemplate.postForEntity(url, data, String.class, "t12611");
			Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
		}

//		try {
//			Thread.sleep(5000l);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
	}

	private String createURLWithPort(final String uri) {
		return "http://localhost:" + port  + uri;
	}
}
