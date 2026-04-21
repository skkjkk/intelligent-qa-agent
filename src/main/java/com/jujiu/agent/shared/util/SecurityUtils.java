package com.jujiu.agent.shared.util;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/2 11:33
 */
public final class SecurityUtils {
    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        UsernamePasswordAuthenticationToken authentication =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        return (Long) authentication.getDetails();
    }
}
