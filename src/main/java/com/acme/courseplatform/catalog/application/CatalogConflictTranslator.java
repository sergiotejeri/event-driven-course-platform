package com.acme.courseplatform.catalog.application;

import com.acme.courseplatform.shared.api.ConflictException;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class CatalogConflictTranslator {

  public <T> T categoryName(Supplier<T> operation) {
    return translate(
        operation, "CATEGORY_NAME_CONFLICT", "A category with this name already exists");
  }

  public <T> T instructorEmail(Supplier<T> operation) {
    return translate(
        operation, "INSTRUCTOR_EMAIL_CONFLICT", "An instructor with this email already exists");
  }

  private <T> T translate(Supplier<T> operation, String errorCode, String detail) {
    try {
      return operation.get();
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(errorCode, detail);
    }
  }
}
