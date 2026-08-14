package com.acme.courseplatform.catalog.domain;

import java.util.UUID;

public class Category {

  private final UUID id;
  private final String name;
  private final String description;
  private CategoryStatus status;

  private Category(UUID id, String name, String description, CategoryStatus status) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.status = status;
  }

  public static Category active(UUID id, String name, String description) {
    return new Category(id, name, description, CategoryStatus.ACTIVE);
  }

  public static Category restore(UUID id, String name, String description, CategoryStatus status) {
    return new Category(id, name, description, status);
  }

  public boolean archive() {
    if (status == CategoryStatus.ARCHIVED) {
      return false;
    }

    status = CategoryStatus.ARCHIVED;
    return true;
  }

  public CategoryStatus status() {
    return status;
  }
}
