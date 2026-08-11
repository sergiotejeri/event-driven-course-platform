package com.acme.courseplatform.payment.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPaymentRepository extends JpaRepository<PaymentJpaEntity, UUID> {

  Optional<PaymentJpaEntity> findByEnrollmentId(UUID enrollmentId);
}
