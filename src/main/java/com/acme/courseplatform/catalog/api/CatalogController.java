package com.acme.courseplatform.catalog.api;

import com.acme.courseplatform.catalog.application.CategoryService;
import com.acme.courseplatform.catalog.application.CourseSearchService;
import com.acme.courseplatform.catalog.application.CourseService;
import com.acme.courseplatform.catalog.application.InstructorService;
import com.acme.courseplatform.catalog.application.model.CategoryView;
import com.acme.courseplatform.catalog.application.model.CourseData;
import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.model.CursorPage;
import com.acme.courseplatform.catalog.application.model.InstructorView;
import com.acme.courseplatform.catalog.application.model.PageResult;
import com.acme.courseplatform.catalog.infrastructure.cache.CatalogCache;
import com.acme.courseplatform.identity.application.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.security.core.Authentication;
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
  private final AuthorizationService authorization;
  private final CatalogCache cache;

  public CatalogController(
      CategoryService categories,
      InstructorService instructors,
      CourseService courses,
      CourseSearchService search,
      AuthorizationService authorization,
      CatalogCache cache) {
    this.categories = categories;
    this.instructors = instructors;
    this.courses = courses;
    this.search = search;
    this.authorization = authorization;
    this.cache = cache;
  }

  @PostMapping("/categories")
  @Operation(summary = "Crear una categoría", security = @SecurityRequirement(name = "bearerAuth"))
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
  @Operation(
      summary = "Actualizar una categoría",
      security = @SecurityRequirement(name = "bearerAuth"))
  CategoryView updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return categories.update(id, request.name(), request.description());
  }

  @PostMapping("/categories/{id}/archive")
  CategoryView archiveCategory(@PathVariable UUID id) {
    return categories.archive(id);
  }

  @DeleteMapping("/categories/{id}")
  @Operation(
      summary = "Eliminar una categoría",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
    categories.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/instructors")
  @Operation(summary = "Crear un instructor", security = @SecurityRequirement(name = "bearerAuth"))
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
  @Operation(
      summary = "Actualizar un instructor",
      security = @SecurityRequirement(name = "bearerAuth"))
  InstructorView updateInstructor(
      @PathVariable UUID id, @Valid @RequestBody InstructorRequest request) {
    return instructors.update(id, request.name(), request.email(), request.biography());
  }

  @DeleteMapping("/instructors/{id}")
  @Operation(
      summary = "Eliminar un instructor",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<Void> deleteInstructor(@PathVariable UUID id) {
    instructors.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/courses")
  @Operation(summary = "Crear un curso", security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<CourseView> createCourse(
      @Valid @RequestBody CourseRequest request, Authentication authentication) {
    authorization.requireCourseInstructorOrAdmin(authentication, request.instructorId());
    CourseView created = courses.create(request.data());
    cache.invalidateSearch();
    return ResponseEntity.created(URI.create("/api/v1/courses/" + created.id())).body(created);
  }

  @GetMapping("/courses/{id}")
  CourseView course(@PathVariable UUID id) {
    return cache.course(id, () -> courses.get(id));
  }

  @PutMapping("/courses/{id}")
  @Operation(summary = "Actualizar un curso", security = @SecurityRequirement(name = "bearerAuth"))
  CourseView updateCourse(
      @PathVariable UUID id,
      @Valid @RequestBody CourseRequest request,
      Authentication authentication) {
    authorization.requireCourseOwnerOrAdmin(authentication, id);
    authorization.requireCourseInstructorOrAdmin(authentication, request.instructorId());
    CourseView updated = courses.update(id, request.data());
    cache.evictCourse(id);
    cache.invalidateSearch();
    return updated;
  }

  @PostMapping("/courses/{id}/publish")
  @Operation(summary = "Publicar un curso", security = @SecurityRequirement(name = "bearerAuth"))
  CourseView publishCourse(@PathVariable UUID id, Authentication authentication) {
    authorization.requireCourseOwnerOrAdmin(authentication, id);
    CourseView published = courses.publish(id);
    cache.evictCourse(id);
    cache.invalidateSearch();
    return published;
  }

  @PostMapping("/courses/{id}/archive")
  @Operation(summary = "Archivar un curso", security = @SecurityRequirement(name = "bearerAuth"))
  CourseView archiveCourse(@PathVariable UUID id, Authentication authentication) {
    authorization.requireCourseOwnerOrAdmin(authentication, id);
    CourseView archived = courses.archive(id);
    cache.evictCourse(id);
    cache.invalidateSearch();
    return archived;
  }

  @DeleteMapping("/courses/{id}")
  @Operation(summary = "Eliminar un curso", security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<Void> deleteCourse(@PathVariable UUID id, Authentication authentication) {
    authorization.requireCourseOwnerOrAdmin(authentication, id);
    courses.delete(id);
    cache.evictCourse(id);
    cache.invalidateSearch();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/courses/search")
  PageResult<CourseView> searchCourses(
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) String level,
      @RequestParam(required = false) @DecimalMin("0.00") BigDecimal minPrice,
      @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxPrice,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) Boolean available,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort) {
    String signature =
        String.join(
            ":",
            value(categoryId),
            value(level),
            value(minPrice),
            value(maxPrice),
            value(title),
            value(available),
            Integer.toString(page),
            Integer.toString(size),
            value(sort));
    return cache.search(
        signature,
        () ->
            search.search(
                categoryId, level, minPrice, maxPrice, title, available, page, size, sort));
  }

  @GetMapping("/courses/search/cursor")
  CursorPage<CourseView> cursorCourses(
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") @Positive int size) {
    return search.cursor(cursor, size);
  }

  private static String value(Object value) {
    return value == null ? "" : value.toString();
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
