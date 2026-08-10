package com.acme.courseplatform.messaging.infrastructure;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

  public static final String EXCHANGE = "course-platform.events";
  public static final String DLX = "course-platform.dlx";
  public static final String PAYMENT_QUEUE = "payment.enrollment-created.v1";
  public static final String PAYMENT_DLQ = PAYMENT_QUEUE + ".dlq";
  public static final String SIMULATION_QUEUE = "payment.simulation-requested.v1";
  public static final String SIMULATION_DLQ = SIMULATION_QUEUE + ".dlq";
  public static final String RESULT_QUEUE = "enrollment.payment-result.v1";
  public static final String RESULT_DLQ = RESULT_QUEUE + ".dlq";
  public static final String CERTIFICATE_QUEUE = "certificate.enrollment-completed.v1";
  public static final String CERTIFICATE_DLQ = CERTIFICATE_QUEUE + ".dlq";

  @Bean
  public Declarables coursePlatformTopology() {
    TopicExchange events = new TopicExchange(EXCHANGE, true, false);
    DirectExchange deadLetters = new DirectExchange(DLX, true, false);
    Queue payment =
        QueueBuilder.durable(PAYMENT_QUEUE)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(PAYMENT_DLQ)
            .build();
    Queue paymentDlq = QueueBuilder.durable(PAYMENT_DLQ).build();
    Queue simulation =
        QueueBuilder.durable(SIMULATION_QUEUE)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(SIMULATION_DLQ)
            .build();
    Queue simulationDlq = QueueBuilder.durable(SIMULATION_DLQ).build();
    Queue result =
        QueueBuilder.durable(RESULT_QUEUE)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(RESULT_DLQ)
            .build();
    Queue resultDlq = QueueBuilder.durable(RESULT_DLQ).build();
    Queue certificate =
        QueueBuilder.durable(CERTIFICATE_QUEUE)
            .deadLetterExchange(DLX)
            .deadLetterRoutingKey(CERTIFICATE_DLQ)
            .build();
    Queue certificateDlq = QueueBuilder.durable(CERTIFICATE_DLQ).build();

    return new Declarables(
        events,
        deadLetters,
        payment,
        paymentDlq,
        simulation,
        simulationDlq,
        result,
        resultDlq,
        certificate,
        certificateDlq,
        BindingBuilder.bind(payment).to(events).with("enrollment.created.v1"),
        BindingBuilder.bind(paymentDlq).to(deadLetters).with(PAYMENT_DLQ),
        BindingBuilder.bind(simulation).to(events).with("payment.simulation-requested.v1"),
        BindingBuilder.bind(simulationDlq).to(deadLetters).with(SIMULATION_DLQ),
        BindingBuilder.bind(result).to(events).with("payment.confirmed.v1"),
        BindingBuilder.bind(result).to(events).with("payment.failed.v1"),
        BindingBuilder.bind(resultDlq).to(deadLetters).with(RESULT_DLQ),
        BindingBuilder.bind(certificate).to(events).with("enrollment.completed.v1"),
        BindingBuilder.bind(certificateDlq).to(deadLetters).with(CERTIFICATE_DLQ));
  }
}
