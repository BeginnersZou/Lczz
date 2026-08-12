package com.lczz.auth.security;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import org.springframework.stereotype.Component;

@Component
public class AuthorizationPolicy {
    public boolean canReadProduct(AuthenticatedUser actor) {
        return actor.roles().stream().anyMatch(role -> role == RoleCode.ADMIN || role == RoleCode.CUSTOMER
                || role == RoleCode.INSTALLER || role == RoleCode.DEALER);
    }

    public boolean canManageProduct(AuthenticatedUser actor) {
        return actor.hasRole(RoleCode.ADMIN);
    }

    public boolean canViewOrder(AuthenticatedUser actor, long customerUserId, Long installerUserId) {
        if (actor.hasRole(RoleCode.ADMIN)) return true;
        if (actor.hasRole(RoleCode.INSTALLER) && installerUserId != null && actor.userId() == installerUserId) return true;
        return (actor.hasRole(RoleCode.CUSTOMER) || actor.hasRole(RoleCode.DEALER))
                && actor.userId() == customerUserId;
    }

    public boolean canEditOrder(AuthenticatedUser actor) {
        return actor.hasRole(RoleCode.ADMIN);
    }

    public boolean canSelectMaterials(AuthenticatedUser actor, Long installerUserId) {
        return actor.hasRole(RoleCode.INSTALLER) && installerUserId != null && actor.userId() == installerUserId;
    }

    public boolean canReview(AuthenticatedUser actor, long customerUserId) {
        return (actor.hasRole(RoleCode.CUSTOMER) || actor.hasRole(RoleCode.DEALER))
                && actor.userId() == customerUserId;
    }
}
