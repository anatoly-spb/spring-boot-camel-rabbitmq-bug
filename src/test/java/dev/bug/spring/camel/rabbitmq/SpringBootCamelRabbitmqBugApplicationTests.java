package dev.bug.spring.camel.rabbitmq;

import static org.junit.Assert.assertTrue;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.MockEndpoints;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@CamelSpringBootTest
@Testcontainers
@Import(dev.bug.spring.camel.rabbitmq.SpringBootCamelRabbitmqBugApplicationTests.SpringBootCamelRabbitmqBugConfig.class)
@MockEndpoints("direct:finish")
class SpringBootCamelRabbitmqBugApplicationTests {
  
  @ClassRule
  public static GenericContainer<?> rabbit = new GenericContainer<>("rabbitmq:management")
      .withExposedPorts(5672, 15672);
  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
      rabbit.start();
      registry.add("spring.rabbitmq.host", rabbit::getHost);
      registry.add("spring.rabbitmq.port", rabbit::getFirstMappedPort);
      registry.add("spring.rabbitmq.username", ()->"guest");
      registry.add("spring.rabbitmq.password", ()->"guest");
  }

  @TestConfiguration
  public static class SpringBootCamelRabbitmqBugConfig extends RouteBuilder { 

    @Override
    public void configure() throws Exception {
      // @formatter:off
      from("direct:start")
        .routeId("start")
        .log("${body}")
        .to("direct:process");
      from("direct:process")
        .routeId("process")
        .log("${body}")
        .log("send to invalid rabbitmq exchange")
        .to("spring-rabbitmq:invalidExchange")
        .log("delay 5 seconds to see error message from RabbitTemplate")
        .delay(5000)
        .to("direct:finish")
      ;
      from("direct:finish")
      .routeId("finish")
      .log("${body}")
      .log("success")
      ;
      // @formatter:on
      
    }
  }
  
  @Autowired
  protected CamelContext camelContext;

  @EndpointInject("mock:direct:finish")
  MockEndpoint mockEndpoint;

  @Produce("direct:start")
  ProducerTemplate producerTemplate;

  @Test
  void contextLoads() throws Exception {
    assertTrue(rabbit.isRunning());
    this.mockEndpoint.setExpectedMessageCount(0);
    this.producerTemplate.sendBody("test");
    this.mockEndpoint.assertIsSatisfied();
  }

}
