package com.acme.courseplatform.catalog.application.port;

import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.model.StudentView;
import java.util.UUID;

public interface StudentCatalogStore {

  PageResult<StudentView> list(int page, int size);

  StudentView get(UUID id);

  StudentView create(String firstName, String lastName, String email);

  StudentView update(UUID id, String firstName, String lastName, String email);
}
