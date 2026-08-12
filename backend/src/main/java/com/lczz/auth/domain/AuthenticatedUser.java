package com.lczz.auth.domain;

import java.util.Set;

public record AuthenticatedUser(long userId, String username, String displayName, String phone, Set<RoleCode> roles) {
    public boolean hasRole(RoleCode role) {
        return roles.contains(role);
    }
}
