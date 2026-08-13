package com.acme.courseplatform.mcp;

import com.acme.courseplatform.identity.application.AuthorizationService;
import com.acme.courseplatform.identity.application.CurrentActor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class McpCurrentActor {
  private final AuthorizationService authorization;

  public McpCurrentActor(AuthorizationService authorization) {
    this.authorization = authorization;
  }

  public CurrentActor current() {
    return authorization.actor(SecurityContextHolder.getContext().getAuthentication());
  }
}
