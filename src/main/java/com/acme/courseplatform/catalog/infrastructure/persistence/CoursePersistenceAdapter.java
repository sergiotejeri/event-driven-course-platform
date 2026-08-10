package com.acme.courseplatform.catalog.infrastructure.persistence;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.acme.courseplatform.catalog.application.model.CourseData;
import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.port.CourseRepository;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class CoursePersistenceAdapter implements CourseRepository {

  private final SpringDataCourseRepository repository;

  public CoursePersistenceAdapter(SpringDataCourseRepository repository) {
    this.repository = repository;
  }

  @Override
  public CourseView create(CourseData data) {
    return view(repository.save(CourseEntity.draft(UUID.randomUUID(), data)));
  }

  @Override
  public CourseView get(UUID id) {
    return view(required(id));
  }

  @Override
  public CourseView update(UUID id, CourseData data) {
    CourseEntity course = required(id);
    course.update(data);
    return view(course);
  }

  @Override
  public CourseView publish(UUID id) {
    CourseEntity course = required(id);
    course.publish();
    return view(course);
  }

  @Override
  public CourseView archive(UUID id) {
    CourseEntity course = required(id);
    course.archive();
    return view(course);
  }

  @Override
  public void delete(UUID id) {
    repository.delete(required(id));
  }

  private CourseEntity required(UUID id) {
    return repository.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
  }

  private static CourseView view(CourseEntity course) {
    return new CourseView(
        course.id(),
        course.title(),
        course.description(),
        course.estimatedHours(),
        course.level(),
        course.price(),
        course.currency(),
        course.capacity(),
        course.occupiedSeats(),
        course.status().name(),
        course.categoryId(),
        course.instructorId());
  }
}
