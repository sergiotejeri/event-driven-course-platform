package com.acme.courseplatform.catalog.application.model;

import java.util.List;

public record CursorPage<T>(List<T> content, String nextCursor) {}
