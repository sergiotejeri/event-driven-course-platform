package com.acme.courseplatform.catalog.application.model;

import java.util.UUID;

public record InstructorView(UUID id, String name, String email, String biography) {}
