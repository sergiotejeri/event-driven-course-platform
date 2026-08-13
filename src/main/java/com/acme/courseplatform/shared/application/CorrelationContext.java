package com.acme.courseplatform.shared.application;

import java.util.UUID;

public interface CorrelationContext {

  UUID currentId();
}
