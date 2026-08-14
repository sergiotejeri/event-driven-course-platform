package com.acme.courseplatform.catalog.application;

import com.acme.courseplatform.catalog.application.model.CategoryView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.port.CatalogStore;
import com.acme.courseplatform.catalog.domain.Category;
import com.acme.courseplatform.catalog.domain.CategoryStatus;
import com.acme.courseplatform.shared.query.SortDirection;
import com.acme.courseplatform.shared.query.SortSpec;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

  private final CatalogStore store;
  private final CatalogConflictTranslator conflicts;

  public CategoryService(CatalogStore store, CatalogConflictTranslator conflicts) {
    this.store = store;
    this.conflicts = conflicts;
  }

  @Transactional
  public CategoryView create(String name, String description) {
    return conflicts.categoryName(() -> store.createCategory(name, description));
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
  public CategoryView archive(UUID id) {
    CategoryView current = store.getCategory(id);
    Category category =
        Category.restore(
            current.id(),
            current.name(),
            current.description(),
            CategoryStatus.valueOf(current.status()));
    if (category.archive()) {
      return store.changeCategoryStatus(id, category.status());
    }
    return current;
  }

  @Transactional
  public void delete(UUID id) {
    store.deleteCategory(id);
  }

  @Transactional(readOnly = true)
  public PageResult<CategoryView> list(int page, int size, String sort) {
    SortSpec spec =
        SortSpec.parse(
            sort,
            Map.of("createdAt", "created_at", "name", "name"),
            "createdAt",
            SortDirection.DESC,
            "id");
    return store.listCategories(page, size, spec);
  }
}
