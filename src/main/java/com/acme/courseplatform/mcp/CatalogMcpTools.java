package com.acme.courseplatform.mcp;

import com.acme.courseplatform.catalog.application.CategoryService;
import com.acme.courseplatform.catalog.application.CourseSearchService;
import com.acme.courseplatform.catalog.application.CourseService;
import com.acme.courseplatform.catalog.application.InstructorService;
import com.acme.courseplatform.catalog.application.StudentService;
import com.acme.courseplatform.catalog.application.model.CategoryView;
import com.acme.courseplatform.catalog.application.model.CourseData;
import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.InstructorView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.application.model.StudentView;
import com.acme.courseplatform.identity.application.AuthorizationService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class CatalogMcpTools {
  private final CategoryService categories;
  private final InstructorService instructors;
  private final StudentService students;
  private final CourseService courses;
  private final CourseSearchService search;
  private final McpCurrentActor actor;
  private final AuthorizationService authorization;

  public CatalogMcpTools(
      CategoryService categories,
      InstructorService instructors,
      StudentService students,
      CourseService courses,
      CourseSearchService search,
      McpCurrentActor actor,
      AuthorizationService authorization) {
    this.categories = categories;
    this.instructors = instructors;
    this.students = students;
    this.courses = courses;
    this.search = search;
    this.actor = actor;
    this.authorization = authorization;
  }

  @McpTool(name = "list_courses", description = "List published courses with pagination")
  public PageResult<CourseView> listCourses(int page, int size, String sort) {
    return search.search(null, null, null, null, null, null, page, size, sort);
  }

  @McpTool(name = "search_courses", description = "Search published courses by catalog filters")
  public PageResult<CourseView> searchCourses(
      UUID categoryId,
      String level,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      String title,
      Boolean available,
      int page,
      int size,
      String sort) {
    return search.search(categoryId, level, minPrice, maxPrice, title, available, page, size, sort);
  }

  @McpTool(name = "get_course", description = "Get one course by identifier")
  public CourseView getCourse(UUID id) {
    return courses.get(id);
  }

  @McpTool(name = "create_course", description = "Create a draft course")
  public CourseView createCourse(
      String title,
      String description,
      int estimatedHours,
      String level,
      BigDecimal price,
      String currency,
      int capacity,
      UUID categoryId,
      UUID instructorId) {
    authorization.requireCourseInstructorOrAdmin(actor.current(), instructorId);
    return courses.create(
        data(
            title,
            description,
            estimatedHours,
            level,
            price,
            currency,
            capacity,
            categoryId,
            instructorId));
  }

  @McpTool(name = "update_course", description = "Update an owned course")
  public CourseView updateCourse(
      UUID id,
      String title,
      String description,
      int estimatedHours,
      String level,
      BigDecimal price,
      String currency,
      int capacity,
      UUID categoryId,
      UUID instructorId) {
    authorization.requireCourseOwnerOrAdmin(actor.current(), id);
    authorization.requireCourseInstructorOrAdmin(actor.current(), instructorId);
    return courses.update(
        id,
        data(
            title,
            description,
            estimatedHours,
            level,
            price,
            currency,
            capacity,
            categoryId,
            instructorId));
  }

  @McpTool(name = "delete_course", description = "Delete an owned course")
  public void deleteCourse(UUID id) {
    authorization.requireCourseOwnerOrAdmin(actor.current(), id);
    courses.delete(id);
  }

  @McpTool(name = "publish_course", description = "Publish an owned draft course")
  public CourseView publishCourse(UUID id) {
    authorization.requireCourseOwnerOrAdmin(actor.current(), id);
    return courses.publish(id);
  }

  @McpTool(name = "archive_course", description = "Archive an owned course")
  public CourseView archiveCourse(UUID id) {
    authorization.requireCourseOwnerOrAdmin(actor.current(), id);
    return courses.archive(id);
  }

  @McpTool(name = "list_categories", description = "List categories with pagination")
  public PageResult<CategoryView> listCategories(int page, int size, String sort) {
    return categories.list(page, size, sort);
  }

  @McpTool(name = "get_category", description = "Get one category")
  public CategoryView getCategory(UUID id) {
    return categories.get(id);
  }

  @McpTool(name = "create_category", description = "Create a category as administrator")
  public CategoryView createCategory(String name, String description) {
    authorization.requireAdmin(actor.current());
    return categories.create(name, description);
  }

  @McpTool(name = "update_category", description = "Update a category as administrator")
  public CategoryView updateCategory(UUID id, String name, String description) {
    authorization.requireAdmin(actor.current());
    return categories.update(id, name, description);
  }

  @McpTool(name = "delete_category", description = "Delete a category as administrator")
  public void deleteCategory(UUID id) {
    authorization.requireAdmin(actor.current());
    categories.delete(id);
  }

  @McpTool(name = "list_instructors", description = "List instructors as administrator")
  public PageResult<InstructorView> listInstructors(int page, int size, String sort) {
    authorization.requireAdmin(actor.current());
    return instructors.list(page, size, sort);
  }

  @McpTool(name = "get_instructor", description = "Get one instructor as administrator")
  public InstructorView getInstructor(UUID id) {
    authorization.requireAdmin(actor.current());
    return instructors.get(id);
  }

  @McpTool(name = "create_instructor", description = "Create an instructor as administrator")
  public InstructorView createInstructor(String name, String email, String biography) {
    authorization.requireAdmin(actor.current());
    return instructors.create(name, email, biography);
  }

  @McpTool(name = "update_instructor", description = "Update an instructor as administrator")
  public InstructorView updateInstructor(UUID id, String name, String email, String biography) {
    authorization.requireAdmin(actor.current());
    return instructors.update(id, name, email, biography);
  }

  @McpTool(name = "delete_instructor", description = "Delete an instructor as administrator")
  public void deleteInstructor(UUID id) {
    authorization.requireAdmin(actor.current());
    instructors.delete(id);
  }

  @McpTool(name = "list_students", description = "List students as administrator")
  public PageResult<StudentView> listStudents(int page, int size) {
    authorization.requireAdmin(actor.current());
    return students.list(page, size);
  }

  @McpTool(name = "get_student", description = "Get a student as owner or administrator")
  public StudentView getStudent(UUID id) {
    authorization.requireStudentOwnerOrAdmin(actor.current(), id);
    return students.get(id);
  }

  @McpTool(name = "create_student", description = "Create a student as administrator")
  public StudentView createStudent(String firstName, String lastName, String email) {
    authorization.requireAdmin(actor.current());
    return students.create(firstName, lastName, email);
  }

  @McpTool(name = "update_student", description = "Update a student as owner or administrator")
  public StudentView updateStudent(UUID id, String firstName, String lastName, String email) {
    authorization.requireStudentOwnerOrAdmin(actor.current(), id);
    return students.update(id, firstName, lastName, email);
  }

  private static CourseData data(
      String title,
      String description,
      int estimatedHours,
      String level,
      BigDecimal price,
      String currency,
      int capacity,
      UUID categoryId,
      UUID instructorId) {
    return new CourseData(
        title,
        description,
        estimatedHours,
        level,
        price,
        currency,
        capacity,
        categoryId,
        instructorId);
  }
}
