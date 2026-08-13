package com.acme.courseplatform.catalog.application;

import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.model.StudentView;
import com.acme.courseplatform.catalog.application.port.StudentCatalogStore;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentService {

  private final StudentCatalogStore store;

  public StudentService(StudentCatalogStore store) {
    this.store = store;
  }

  @Transactional(readOnly = true)
  public PageResult<StudentView> list(int page, int size) {
    validatePage(page, size);
    return store.list(page, size);
  }

  @Transactional(readOnly = true)
  public StudentView get(UUID id) {
    return store.get(id);
  }

  @Transactional
  public StudentView create(String firstName, String lastName, String email) {
    return store.create(firstName, lastName, email);
  }

  @Transactional
  public StudentView update(UUID id, String firstName, String lastName, String email) {
    return store.update(id, firstName, lastName, email);
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > 100) {
      throw new IllegalArgumentException("Invalid page or size");
    }
  }
}
