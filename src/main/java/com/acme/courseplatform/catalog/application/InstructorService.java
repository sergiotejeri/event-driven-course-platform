package com.acme.courseplatform.catalog.application;

import com.acme.courseplatform.catalog.application.model.InstructorView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.port.CatalogStore;
import com.acme.courseplatform.shared.query.SortDirection;
import com.acme.courseplatform.shared.query.SortSpec;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstructorService {

  private final CatalogStore store;
  private final CatalogConflictTranslator conflicts;

  public InstructorService(CatalogStore store, CatalogConflictTranslator conflicts) {
    this.store = store;
    this.conflicts = conflicts;
  }

  @Transactional
  public InstructorView create(String name, String email, String biography) {
    return conflicts.instructorEmail(() -> store.createInstructor(name, email, biography));
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
  public PageResult<InstructorView> list(int page, int size, String sort) {
    SortSpec spec =
        SortSpec.parse(
            sort,
            Map.of("createdAt", "created_at", "name", "name", "email", "email"),
            "createdAt",
            SortDirection.DESC,
            "id");
    return store.listInstructors(page, size, spec);
  }

  @Transactional
  public void delete(UUID id) {
    store.deleteInstructor(id);
  }
}
