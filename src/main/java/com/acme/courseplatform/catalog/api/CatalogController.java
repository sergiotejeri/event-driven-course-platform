package com.acme.courseplatform.catalog.api;

import com.acme.courseplatform.catalog.application.CategoryService;
import com.acme.courseplatform.catalog.application.CourseSearchService;
import com.acme.courseplatform.catalog.application.CourseService;
import com.acme.courseplatform.catalog.application.InstructorService;
import com.acme.courseplatform.catalog.application.model.CategoryView;
import com.acme.courseplatform.catalog.application.model.CourseData;
import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.InstructorView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {

  private final CategoryService categories;
  private final InstructorService instructors;
  private final CourseService courses;
  private final CourseSearchService search;

  public CatalogController(
      CategoryService categories,
      InstructorService instructors,
      CourseService courses,
      CourseSearchService search) {
    this.categories = categories;
    this.instructors = instructors;
    this.courses = courses;
    this.search = search;
  }

  @PostMapping("/categories")
  ResponseEntity<CategoryView> createCategory(@Valid @RequestBody CategoryRequest request) {
    CategoryView created = categories.create(request.name(), request.description());
    return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
  }

  @GetMapping("/categories")
  PageResult<CategoryView> categories(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort) {
    return categories.list(page, size, sort);
  }

  @GetMapping("/categories/{id}")
  CategoryView category(@PathVariable UUID id) {
    return categories.get(id);
  }

  @PutMapping("/categories/{id}")
  CategoryView updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return categories.update(id, request.name(), request.description());
  }

  @DeleteMapping("/categories/{id}")
  ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
    categories.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/instructors")
  ResponseEntity<InstructorView> createInstructor(@Valid @RequestBody InstructorRequest request) {
    InstructorView created =
        instructors.create(request.name(), request.email(), request.biography());
    return ResponseEntity.created(URI.create("/api/v1/instructors/" + created.id())).body(created);
  }

  @GetMapping("/instructors")
  PageResult<InstructorView> instructors(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort) {
    return instructors.list(page, size, sort);
  }

  @GetMapping("/instructors/{id}")
  InstructorView instructor(@PathVariable UUID id) {
    return instructors.get(id);
  }

  @PutMapping("/instructors/{id}")
  InstructorView updateInstructor(
      @PathVariable UUID id, @Valid @RequestBody InstructorRequest request) {
    return instructors.update(id, request.name(), request.email(), request.biography());
  }

  @DeleteMapping("/instructors/{id}")
  ResponseEntity<Void> deleteInstructor(@PathVariable UUID id) {
    instructors.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/courses")
  ResponseEntity<CourseView> createCourse(@Valid @RequestBody CourseRequest request) {
    CourseView created = courses.create(request.data());
    return ResponseEntity.created(URI.create("/api/v1/courses/" + created.id())).body(created);
  }

  @GetMapping("/courses/{id}")
  CourseView course(@PathVariable UUID id) {
    return courses.get(id);
  }

  @PutMapping("/courses/{id}")
  CourseView updateCourse(@PathVariable UUID id, @Valid @RequestBody CourseRequest request) {
    return courses.update(id, request.data());
  }

  @PostMapping("/courses/{id}/publish")
  CourseView publishCourse(@PathVariable UUID id) {
    return courses.publish(id);
  }

  @PostMapping("/courses/{id}/archive")
  CourseView archiveCourse(@PathVariable UUID id) {
    return courses.archive(id);
  }

  @DeleteMapping("/courses/{id}")
  ResponseEntity<Void> deleteCourse(@PathVariable UUID id) {
    courses.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/courses/search")
  PageResult<CourseView> searchCourses(
      @RequestParam(required = false) String level,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) Boolean available,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort) {
    return search.search(level, title, available, page, size, sort);
  }

  @GetMapping("/courses/search/cursor")
  PageResult<CourseView> cursorCourses(@RequestParam(defaultValue = "20") @Positive int size) {
    return search.cursor(size);
  }

  record CategoryRequest(@NotBlank String name, @NotNull String description) {}

  record InstructorRequest(
      @NotBlank String name, @NotBlank String email, @NotNull String biography) {}

  record CourseRequest(
      @NotBlank String title,
      @NotNull String description,
      @Min(0) int estimatedHours,
      @NotBlank String level,
      @NotNull @DecimalMin("0.00") BigDecimal price,
      @NotBlank String currency,
      @Positive int capacity,
      @NotNull UUID categoryId,
      @NotNull UUID instructorId) {

    CourseData data() {
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
}
