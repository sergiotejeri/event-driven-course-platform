package com.acme.courseplatform.catalog.application.model;

import java.util.UUID;

public record CategoryView(UUID id, String name, String description, String status) {}
