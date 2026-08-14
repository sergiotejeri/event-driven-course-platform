package com.acme.courseplatform.catalog.application.port;

import com.acme.courseplatform.catalog.application.model.CategoryView;
import com.acme.courseplatform.catalog.application.model.CourseCursor;
import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.CursorPage;
import com.acme.courseplatform.catalog.application.model.InstructorView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.domain.CategoryStatus;
import com.acme.courseplatform.shared.query.SortSpec;
import java.math.BigDecimal;
import java.util.UUID;

public interface CatalogStore {

  CategoryView createCategory(String name, String description);

  CategoryView getCategory(UUID id);

  CategoryView updateCategory(UUID id, String name, String description);

  CategoryView changeCategoryStatus(UUID id, CategoryStatus status);

  void deleteCategory(UUID id);

  PageResult<CategoryView> listCategories(int page, int size, SortSpec sort);

  InstructorView createInstructor(String name, String email, String biography);

  InstructorView getInstructor(UUID id);

  InstructorView updateInstructor(UUID id, String name, String email, String biography);

  PageResult<InstructorView> listInstructors(int page, int size, SortSpec sort);

  void deleteInstructor(UUID id);

  PageResult<CourseView> searchCourses(
      UUID categoryId,
      String level,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      String title,
      Boolean available,
      int page,
      int size,
      SortSpec sort);

  CursorPage<CourseView> cursorCourses(CourseCursor cursor, int size);
}
