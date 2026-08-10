package com.acme.courseplatform.catalog.application.port;

import com.acme.courseplatform.catalog.application.model.CourseData;
import com.acme.courseplatform.catalog.application.model.CourseView;
import java.util.UUID;

public interface CourseRepository {

  CourseView create(CourseData data);

  CourseView get(UUID id);

  CourseView update(UUID id, CourseData data);

  CourseView publish(UUID id);

  CourseView archive(UUID id);

  void delete(UUID id);
}
