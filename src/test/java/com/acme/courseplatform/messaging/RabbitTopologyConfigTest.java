package com.acme.courseplatform.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.courseplatform.messaging.infrastructure.RabbitTopologyConfig;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

class RabbitTopologyConfigTest {

  @Test
  void declaresTheFiveEventRoutesAndTheSimulationDeadLetterQueue() {
    Declarables topology = new RabbitTopologyConfig().coursePlatformTopology();

    Set<String> queues =
        topology.getDeclarablesByType(Queue.class).stream()
            .map(Queue::getName)
            .collect(Collectors.toSet());
    Map<String, String> routes =
        topology.getDeclarablesByType(Binding.class).stream()
            .collect(Collectors.toMap(Binding::getRoutingKey, Binding::getDestination));
    Queue simulationQueue =
        topology.getDeclarablesByType(Queue.class).stream()
            .filter(queue -> queue.getName().equals(RabbitTopologyConfig.SIMULATION_QUEUE))
            .findFirst()
            .orElseThrow();

    assertThat(queues)
        .contains(
            RabbitTopologyConfig.PAYMENT_QUEUE,
            RabbitTopologyConfig.PAYMENT_DLQ,
            RabbitTopologyConfig.SIMULATION_QUEUE,
            RabbitTopologyConfig.SIMULATION_DLQ,
            RabbitTopologyConfig.RESULT_QUEUE,
            RabbitTopologyConfig.RESULT_DLQ,
            RabbitTopologyConfig.CERTIFICATE_QUEUE,
            RabbitTopologyConfig.CERTIFICATE_DLQ);
    assertThat(routes)
        .containsEntry("enrollment.created.v1", RabbitTopologyConfig.PAYMENT_QUEUE)
        .containsEntry("payment.simulation-requested.v1", RabbitTopologyConfig.SIMULATION_QUEUE)
        .containsEntry("payment.confirmed.v1", RabbitTopologyConfig.RESULT_QUEUE)
        .containsEntry("payment.failed.v1", RabbitTopologyConfig.RESULT_QUEUE)
        .containsEntry("enrollment.completed.v1", RabbitTopologyConfig.CERTIFICATE_QUEUE)
        .containsEntry(RabbitTopologyConfig.SIMULATION_DLQ, RabbitTopologyConfig.SIMULATION_DLQ);
    assertThat(simulationQueue.getArguments())
        .containsEntry("x-dead-letter-exchange", RabbitTopologyConfig.DLX)
        .containsEntry("x-dead-letter-routing-key", RabbitTopologyConfig.SIMULATION_DLQ);
  }
}
