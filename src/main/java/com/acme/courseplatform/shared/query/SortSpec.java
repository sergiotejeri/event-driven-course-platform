package com.acme.courseplatform.shared.query;

import java.util.Map;

public record SortSpec(String sqlOrder) {

  public static SortSpec parse(
      String input,
      Map<String, String> allowedFields,
      String defaultField,
      SortDirection defaultDirection,
      String tieBreaker) {
    String field = defaultField;
    SortDirection direction = defaultDirection;
    if (input != null && !input.isBlank()) {
      String[] parts = input.split(",", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("Sort must use field,direction");
      }
      field = parts[0];
      direction = SortDirection.parse(parts[1]);
    }
    String column = allowedFields.get(field);
    if (column == null) {
      throw new IllegalArgumentException("Unsupported sort field: " + field);
    }
    String sqlDirection = direction.name().toLowerCase();
    return new SortSpec(column + " " + sqlDirection + "," + tieBreaker + " " + sqlDirection);
  }
}
