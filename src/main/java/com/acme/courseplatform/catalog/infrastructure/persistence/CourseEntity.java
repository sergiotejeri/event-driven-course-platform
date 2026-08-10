package com.acme.courseplatform.catalog.infrastructure.persistence;

import com.acme.courseplatform.catalog.application.model.CourseData;
import com.acme.courseplatform.catalog.domain.CourseStatus;
import com.acme.courseplatform.shared.domain.InvalidTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "courses")
public class CourseEntity {

  @Id private UUID id;

  @Column(length = 240)
  private String title;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "estimated_hours")
  private int estimatedHours;

  @Column(length = 24)
  private String level;

  @Column(precision = 12, scale = 2)
  private BigDecimal price;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 3)
  private String currency;

  private int capacity;

  @Column(name = "occupied_seats")
  private int occupiedSeats;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private CourseStatus status;

  @Column(name = "category_id")
  private UUID categoryId;

  @Column(name = "instructor_id")
  private UUID instructorId;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected CourseEntity() {}

  private CourseEntity(UUID id, CourseData data) {
    this.id = id;
    this.status = CourseStatus.DRAFT;
    this.occupiedSeats = 0;
    this.createdAt = Instant.now();
    update(data);
  }

  public static CourseEntity draft(UUID id, CourseData data) {
    return new CourseEntity(id, data);
  }

  public void update(CourseData data) {
    this.title = data.title();
    this.description = data.description();
    this.estimatedHours = data.estimatedHours();
    this.level = data.level();
    this.price = data.price();
    this.currency = data.currency();
    this.capacity = data.capacity();
    this.categoryId = data.categoryId();
    this.instructorId = data.instructorId();
    this.updatedAt = Instant.now();
  }

  public void publish() {
    if (status != CourseStatus.DRAFT) {
      throw new InvalidTransitionException("Course can only be published from DRAFT");
    }
    status = CourseStatus.PUBLISHED;
    updatedAt = Instant.now();
  }

  public void archive() {
    if (status != CourseStatus.PUBLISHED) {
      throw new InvalidTransitionException("Course can only be archived from PUBLISHED");
    }
    status = CourseStatus.ARCHIVED;
    updatedAt = Instant.now();
  }

  public UUID id() {
    return id;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public int estimatedHours() {
    return estimatedHours;
  }

  public String level() {
    return level;
  }

  public BigDecimal price() {
    return price;
  }

  public String currency() {
    return currency;
  }

  public int capacity() {
    return capacity;
  }

  public int occupiedSeats() {
    return occupiedSeats;
  }

  public CourseStatus status() {
    return status;
  }

  public UUID categoryId() {
    return categoryId;
  }

  public UUID instructorId() {
    return instructorId;
  }
}
