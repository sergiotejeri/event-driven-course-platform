package com.acme.courseplatform.catalog.application.model;

import java.util.UUID;

public record StudentView(UUID id, String firstName, String lastName, String email) {}
