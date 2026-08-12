package com.lczz.auth;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.security.AuthorizationPolicy;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPolicyTests {
    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @Test
    void administratorCanAccessAllOrdersAndManagementOperations() {
        var admin = user(1, RoleCode.ADMIN);
        assertThat(policy.canReadProduct(admin)).isTrue();
        assertThat(policy.canManageProduct(admin)).isTrue();
        assertThat(policy.canViewOrder(admin, 99, 88L)).isTrue();
        assertThat(policy.canEditOrder(admin)).isTrue();
    }

    @Test
    void customerCanOnlyViewOwnOrderAndReviewOwnOrder() {
        var customer = user(10, RoleCode.CUSTOMER);
        assertThat(policy.canReadProduct(customer)).isTrue();
        assertThat(policy.canViewOrder(customer, 10, 20L)).isTrue();
        assertThat(policy.canReview(customer, 10)).isTrue();
        assertThat(policy.canViewOrder(customer, 11, 20L)).isFalse();
        assertThat(policy.canEditOrder(customer)).isFalse();
    }

    @Test
    void installerCanOnlyViewAssignedOrderAndSelectItsMaterials() {
        var installer = user(20, RoleCode.INSTALLER);
        assertThat(policy.canReadProduct(installer)).isTrue();
        assertThat(policy.canViewOrder(installer, 10, 20L)).isTrue();
        assertThat(policy.canSelectMaterials(installer, 20L)).isTrue();
        assertThat(policy.canViewOrder(installer, 10, 21L)).isFalse();
        assertThat(policy.canSelectMaterials(installer, 21L)).isFalse();
    }

    @Test
    void dealerMatchesCustomerAndCannotSeeUnboundCompanyOrder() {
        var dealer = user(30, RoleCode.DEALER);
        assertThat(policy.canReadProduct(dealer)).isTrue();
        assertThat(policy.canViewOrder(dealer, 30, 20L)).isTrue();
        assertThat(policy.canReview(dealer, 30)).isTrue();
        assertThat(policy.canViewOrder(dealer, 31, 20L)).isFalse();
        assertThat(policy.canManageProduct(dealer)).isFalse();
    }

    private AuthenticatedUser user(long id, RoleCode role) {
        return new AuthenticatedUser(id, null, role.name(), null, Set.of(role));
    }
}
