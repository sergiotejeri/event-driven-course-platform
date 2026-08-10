package com.acme.courseplatform.catalog.application.model;

import java.util.List;

public record PageResult<T>(List<T> content, long totalElements, int page, int size) {}
