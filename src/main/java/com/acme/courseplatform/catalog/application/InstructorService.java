package com.acme.courseplatform.catalog.application;

import com.acme.courseplatform.catalog.application.model.InstructorView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.port.CatalogStore;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructorService {

  private final CatalogStore store;

  public InstructorService(CatalogStore store) {
    this.store = store;
  }

  @Transactional
  public InstructorView create(String name, String email, String biography) {
    return store.createInstructor(name, email, biography);
  }

  @Transactional(readOnly = true)
  public InstructorView get(UUID id) {
    return store.getInstructor(id);
  }

  @Transactional
  public InstructorView update(UUID id, String name, String email, String biography) {
    return store.updateInstructor(id, name, email, biography);
  }

  @Transactional(readOnly = true)
  public PageResult<InstructorView> list(int page, int size) {
    return store.listInstructors(page, size);
  }

  @Transactional
  public void delete(UUID id) {
    store.deleteInstructor(id);
  }
}
