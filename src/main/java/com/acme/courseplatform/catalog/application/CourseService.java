package com.acme.courseplatform.catalog.application;

import com.acme.courseplatform.catalog.application.model.CourseData;
import com.acme.courseplatform.catalog.application.model.CourseView;
import com.acme.courseplatform.catalog.application.port.CourseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {

  private final CourseRepository repository;

  public CourseService(CourseRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public CourseView create(CourseData data) {
    return repository.create(data);
  }

  @Transactional(readOnly = true)
  public CourseView get(UUID id) {
    return repository.get(id);
  }

  @Transactional
  public CourseView update(UUID id, CourseData data) {
    return repository.update(id, data);
  }

  @Transactional
  public CourseView publish(UUID id) {
    return repository.publish(id);
  }

  @Transactional
  public CourseView archive(UUID id) {
    return repository.archive(id);
  }

  @Transactional
  public void delete(UUID id) {
    repository.delete(id);
  }
}
