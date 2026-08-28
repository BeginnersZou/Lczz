package com.lczz.auth.security;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.service.UserAccountService;
import com.lczz.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserAccountService userAccountService;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountService userAccountService) {
        this.jwtService = jwtService;
        this.userAccountService = userAccountService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                AuthenticatedUser user = userAccountService.requireActive(jwtService.parseUserId(header.substring(7)));
                var authorities = user.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, null, authorities));
            } catch (BusinessException exception) {
                request.setAttribute("authFailure", exception);
            }
        }
        chain.doFilter(request, response);
    }
}
