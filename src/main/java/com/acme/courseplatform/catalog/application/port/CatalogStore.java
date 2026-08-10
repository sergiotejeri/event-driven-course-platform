package com.acme.courseplatform.catalog.application.port;

import com.acme.courseplatform.catalog.application.model.CategoryView;
import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.InstructorView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import java.util.UUID;

public interface CatalogStore {

  CategoryView createCategory(String name, String description);

  CategoryView getCategory(UUID id);

  CategoryView updateCategory(UUID id, String name, String description);

  void deleteCategory(UUID id);

  PageResult<CategoryView> listCategories(int page, int size);

  InstructorView createInstructor(String name, String email, String biography);

  InstructorView getInstructor(UUID id);

  InstructorView updateInstructor(UUID id, String name, String email, String biography);

  PageResult<InstructorView> listInstructors(int page, int size);

  void deleteInstructor(UUID id);

  PageResult<CourseView> searchCourses(
      String level, String title, Boolean available, int page, int size);

  PageResult<CourseView> cursorCourses(int size);
}
