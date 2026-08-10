package com.acme.courseplatform.catalog.application;

import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.port.CatalogStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseSearchService {

  private final CatalogStore store;

  public CourseSearchService(CatalogStore store) {
    this.store = store;
  }

  @Transactional(readOnly = true)
  public PageResult<CourseView> search(
      String level, String title, Boolean available, int page, int size) {
    return store.searchCourses(level, title, available, page, size);
  }

  @Transactional(readOnly = true)
  public PageResult<CourseView> cursor(int size) {
    return store.cursorCourses(size);
  }
}
