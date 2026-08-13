package com.acme.courseplatform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.acme.courseplatform")
class LayerBoundaryTest {

  @ArchTest
  static final ArchRule controllersDoNotUsePersistenceTechnologies =
      noClasses()
          .that(controllers())
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.jdbc..",
              "jakarta.persistence..",
              "org.springframework.data.jpa..",
              "org.springframework.orm.jpa..",
              "org.hibernate..");

  @ArchTest
  static final ArchRule listenersDoNotUsePersistenceTechnologies =
      noClasses()
          .that(rabbitListeners())
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.jdbc..",
              "jakarta.persistence..",
              "org.springframework.data.jpa..",
              "org.springframework.orm.jpa..",
              "org.hibernate..");

  @ArchTest
  static final ArchRule jpaStaysInPersistenceInfrastructure =
      noClasses()
          .that()
          .resideOutsideOfPackage("..infrastructure.persistence..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "jakarta.persistence..",
              "org.springframework.data.jpa..",
              "org.springframework.orm.jpa..",
              "org.hibernate..");

  @ArchTest
  static final ArchRule apiDoesNotDependOnPersistenceAdapters =
      noClasses()
          .that()
          .resideInAPackage("..api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..infrastructure.persistence..");

  private static DescribedPredicate<JavaClass> controllers() {
    return DescribedPredicate.describe(
        "controllers",
        type ->
            type.getSimpleName().endsWith("Controller")
                || type.isAnnotatedWith(RestController.class)
                || type.isAnnotatedWith(Controller.class)
                || type.isMetaAnnotatedWith(Controller.class));
  }

  private static DescribedPredicate<JavaClass> rabbitListeners() {
    return DescribedPredicate.describe(
        "RabbitMQ listeners",
        type ->
            type.getSimpleName().endsWith("Listener")
                || type.getMethods().stream()
                    .anyMatch(method -> method.isAnnotatedWith(RabbitListener.class)));
  }
}
