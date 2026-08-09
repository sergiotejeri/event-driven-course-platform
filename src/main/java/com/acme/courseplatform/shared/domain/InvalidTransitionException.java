package com.acme.courseplatform.shared.domain;

public class InvalidTransitionException extends RuntimeException {

  public InvalidTransitionException(String message) {
    super(message);
  }
}
