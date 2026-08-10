package com.acme.courseplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CoursePlatformApplication {

  public static void main(String[] args) {
    SpringApplication.run(CoursePlatformApplication.class, args);
  }
}
