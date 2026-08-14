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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
  @Operation(
      summary = "Create a category",
      description = "Creates an active catalog category with a unique name",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Category created"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Administrator role required"),
    @ApiResponse(responseCode = "409", description = "Category name already exists")
  })
  ResponseEntity<CategoryView> createCategory(@Valid @RequestBody CategoryRequest request) {
    CategoryView created = categories.create(request.name(), request.description());
    return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
  }

  @GetMapping("/categories")
  @Operation(
      summary = "List categories",
      description = "Returns categories using offset pagination and allow-listed sorting")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paginated categories"),
    @ApiResponse(responseCode = "400", description = "Invalid page, size or sort")
  })
  PageResult<CategoryView> categories(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort) {
    return categories.list(page, size, sort);
  }

  @GetMapping("/categories/{id}")
  @Operation(summary = "Get a category", description = "Returns a category by identifier")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Category found"),
    @ApiResponse(responseCode = "404", description = "Category not found")
  })
  CategoryView category(@PathVariable UUID id) {
    return categories.get(id);
  }

  @PutMapping("/categories/{id}")
  @Operation(
      summary = "Update a category",
      description = "Replaces the editable data of an existing category",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Category updated"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Administrator role required"),
    @ApiResponse(responseCode = "404", description = "Category not found"),
    @ApiResponse(responseCode = "409", description = "Category name already exists")
  })
  CategoryView updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return categories.update(id, request.name(), request.description());
  }

  @PostMapping("/categories/{id}/archive")
  @Operation(
      summary = "Archive a category",
      description = "Moves an active category to its terminal archived state",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Category archived"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Administrator role required"),
    @ApiResponse(responseCode = "404", description = "Category not found")
  })
  CategoryView archiveCategory(@PathVariable UUID id) {
    return categories.archive(id);
  }

  @DeleteMapping("/categories/{id}")
  @Operation(
      summary = "Delete a category",
      description = "Deletes a category when no persisted relation prevents it",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Category deleted"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Administrator role required"),
    @ApiResponse(responseCode = "404", description = "Category not found"),
    @ApiResponse(responseCode = "409", description = "Category is still referenced")
  })
  ResponseEntity<Void> deleteCategory(@PathVariable UUID id) {
    categories.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/instructors")
  @Operation(
      summary = "Create an instructor",
      description = "Creates an instructor profile with a unique normalized email",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Instructor created"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Administrator role required"),
    @ApiResponse(responseCode = "409", description = "Instructor email already exists")
  })
  ResponseEntity<InstructorView> createInstructor(@Valid @RequestBody InstructorRequest request) {
    InstructorView created =
        instructors.create(request.name(), request.email(), request.biography());
    return ResponseEntity.created(URI.create("/api/v1/instructors/" + created.id())).body(created);
  }

  @GetMapping("/instructors")
  @Operation(
      summary = "List instructors",
      description = "Returns instructors using offset pagination and allow-listed sorting")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paginated instructors"),
    @ApiResponse(responseCode = "400", description = "Invalid page, size or sort")
  })
  PageResult<InstructorView> instructors(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Positive int size,
      @RequestParam(required = false) String sort) {
    return instructors.list(page, size, sort);
  }

  @GetMapping("/instructors/{id}")
  @Operation(summary = "Get an instructor", description = "Returns an instructor by identifier")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Instructor found"),
    @ApiResponse(responseCode = "404", description = "Instructor not found")
  })
  InstructorView instructor(@PathVariable UUID id) {
    return instructors.get(id);
  }

  @PutMapping("/instructors/{id}")
  @Operation(
      summary = "Update an instructor",
      description = "Replaces the editable data of an existing instructor",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Instructor updated"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Administrator role required"),
    @ApiResponse(responseCode = "404", description = "Instructor not found"),
    @ApiResponse(responseCode = "409", description = "Instructor email already exists")
  })
  InstructorView updateInstructor(
      @PathVariable UUID id, @Valid @RequestBody InstructorRequest request) {
    return instructors.update(id, request.name(), request.email(), request.biography());
  }

  @DeleteMapping("/instructors/{id}")
  @Operation(
      summary = "Delete an instructor",
      description = "Deletes an instructor when no persisted relation prevents it",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Instructor deleted"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Administrator role required"),
    @ApiResponse(responseCode = "404", description = "Instructor not found"),
    @ApiResponse(responseCode = "409", description = "Instructor is still referenced")
  })
  ResponseEntity<Void> deleteInstructor(@PathVariable UUID id) {
    instructors.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/courses")
  @Operation(
      summary = "Create a course",
      description =
          "Creates a draft course for an instructor owned by the caller or selected by an administrator",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Course created"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Instructor ownership required"),
    @ApiResponse(responseCode = "404", description = "Category or instructor not found")
  })
  ResponseEntity<CourseView> createCourse(
      @Valid @RequestBody CourseRequest request, Authentication authentication) {
    authorization.requireCourseInstructorOrAdmin(authentication, request.instructorId());
    CourseView created = courses.create(request.data());
    cache.invalidateSearch();
    return ResponseEntity.created(URI.create("/api/v1/courses/" + created.id())).body(created);
  }

  @GetMapping("/courses/{id}")
  @Operation(
      summary = "Get a course",
      description = "Returns a course by identifier and uses the catalog cache")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Course found"),
    @ApiResponse(responseCode = "404", description = "Course not found")
  })
  CourseView course(@PathVariable UUID id) {
    return cache.course(id, () -> courses.get(id));
  }

  @PutMapping("/courses/{id}")
  @Operation(
      summary = "Update a course",
      description = "Updates an owned course and invalidates affected catalog cache entries",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Course updated"),
    @ApiResponse(responseCode = "400", description = "Invalid request body"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Course and instructor ownership required"),
    @ApiResponse(responseCode = "404", description = "Course, category or instructor not found"),
    @ApiResponse(
        responseCode = "409",
        description = "Course cannot be updated in its current state")
  })
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
  @Operation(
      summary = "Publish a course",
      description = "Transitions an owned draft course to published state",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Course published"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Course ownership required"),
    @ApiResponse(responseCode = "404", description = "Course not found"),
    @ApiResponse(responseCode = "409", description = "Invalid course state transition")
  })
  CourseView publishCourse(@PathVariable UUID id, Authentication authentication) {
    authorization.requireCourseOwnerOrAdmin(authentication, id);
    CourseView published = courses.publish(id);
    cache.evictCourse(id);
    cache.invalidateSearch();
    return published;
  }

  @PostMapping("/courses/{id}/archive")
  @Operation(
      summary = "Archive a course",
      description = "Transitions an owned course to archived state",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Course archived"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Course ownership required"),
    @ApiResponse(responseCode = "404", description = "Course not found"),
    @ApiResponse(responseCode = "409", description = "Invalid course state transition")
  })
  CourseView archiveCourse(@PathVariable UUID id, Authentication authentication) {
    authorization.requireCourseOwnerOrAdmin(authentication, id);
    CourseView archived = courses.archive(id);
    cache.evictCourse(id);
    cache.invalidateSearch();
    return archived;
  }

  @DeleteMapping("/courses/{id}")
  @Operation(
      summary = "Delete a course",
      description = "Deletes an owned course when no persisted relation prevents it",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Course deleted"),
    @ApiResponse(responseCode = "401", description = "Authentication required"),
    @ApiResponse(responseCode = "403", description = "Course ownership required"),
    @ApiResponse(responseCode = "404", description = "Course not found"),
    @ApiResponse(responseCode = "409", description = "Course is still referenced")
  })
  ResponseEntity<Void> deleteCourse(@PathVariable UUID id, Authentication authentication) {
    authorization.requireCourseOwnerOrAdmin(authentication, id);
    courses.delete(id);
    cache.evictCourse(id);
    cache.invalidateSearch();
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/courses/search")
  @Operation(
      summary = "Search courses",
      description =
          "Combines category, level, price, title and seat filters with offset pagination and safe sorting")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paginated matching courses"),
    @ApiResponse(responseCode = "400", description = "Invalid filter, page, size or sort")
  })
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
  @Operation(
      summary = "List courses by cursor",
      description =
          "Returns published courses using keyset pagination ordered by creation time and identifier")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Cursor-ordered courses"),
    @ApiResponse(responseCode = "400", description = "Invalid cursor or page size")
  })
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
