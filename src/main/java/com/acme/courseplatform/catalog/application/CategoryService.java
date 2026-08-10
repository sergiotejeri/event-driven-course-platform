package com.acme.courseplatform.catalog.application;

import com.acme.courseplatform.catalog.application.model.CategoryView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.port.CatalogStore;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

  private final CatalogStore store;

  public CategoryService(CatalogStore store) {
    this.store = store;
  }

  @Transactional
  public CategoryView create(String name, String description) {
    return store.createCategory(name, description);
  }

  @Transactional(readOnly = true)
  public CategoryView get(UUID id) {
    return store.getCategory(id);
  }

  @Transactional
  public CategoryView update(UUID id, String name, String description) {
    return store.updateCategory(id, name, description);
  }

  @Transactional
  public void delete(UUID id) {
    store.deleteCategory(id);
  }

  @Transactional(readOnly = true)
  public PageResult<CategoryView> list(int page, int size) {
    return store.listCategories(page, size);
  }
}
