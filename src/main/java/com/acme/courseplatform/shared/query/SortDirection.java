package com.acme.courseplatform.shared.query;

public enum SortDirection {
  ASC,
  DESC;

  static SortDirection parse(String value) {
    for (SortDirection direction : values()) {
      if (direction.name().equalsIgnoreCase(value)) {
        return direction;
      }
    }
    throw new IllegalArgumentException("Unsupported sort direction: " + value);
  }
}
