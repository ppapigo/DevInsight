package com.portfolio.devinsight.security;

import org.springframework.security.core.Authentication;

public final class Access {
  private Access() {}

  public static boolean isAdmin(Authentication auth) {
    return auth != null
        && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
  }
}
