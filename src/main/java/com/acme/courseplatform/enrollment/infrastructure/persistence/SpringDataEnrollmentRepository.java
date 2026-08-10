package com.acme.courseplatform.enrollment.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEnrollmentRepository extends JpaRepository<EnrollmentJpaEntity, UUID> {}
