package com.acme.courseplatform.catalog.domain;

import com.acme.courseplatform.shared.domain.InvalidTransitionException;
import java.util.UUID;

public class Course {

  private final UUID id;
  private final String title;
  private final int capacity;
  private CourseStatus status;

  private Course(UUID id, String title, int capacity, CourseStatus status) {
    this.id = id;
    this.title = title;
    this.capacity = capacity;
    this.status = status;
  }

  public static Course draft(UUID id, String title, int capacity) {
    return new Course(id, title, capacity, CourseStatus.DRAFT);
  }

  public void publish() {
    if (status != CourseStatus.DRAFT) {
      throw new InvalidTransitionException("Course can only be published from DRAFT");
    }

    status = CourseStatus.PUBLISHED;
  }

  public void archive() {
    if (status != CourseStatus.PUBLISHED) {
      throw new InvalidTransitionException("Course can only be archived from PUBLISHED");
    }

    status = CourseStatus.ARCHIVED;
  }

  public CourseStatus status() {
    return status;
  }
}
