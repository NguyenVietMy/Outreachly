package com.outreachly.outreachly.security;

import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

        private final UserService userService;

        @Value("${FRONTEND_URL}")
        private String frontendUrl;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                        Authentication authentication) throws IOException, ServletException {

                log.info("OAuth2 authentication success handler called");

                try {
                        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
                        Map<String, Object> attributes = oAuth2User.getAttributes();

                        String email = (String) attributes.get("email");
                        String firstName = (String) attributes.get("given_name");
                        String lastName = (String) attributes.get("family_name");
                        String profilePictureUrl = (String) attributes.get("picture");
                        String providerId = (String) attributes.get("sub");

                        log.info("OAuth2 login successful for user: {}", email);
                        log.info("User attributes: {}", attributes);

                        // Create or update user in database
                        User user = userService.createOrUpdateUser(
                                        email,
                                        firstName,
                                        lastName,
                                        profilePictureUrl,
                                        User.AuthProvider.GOOGLE,
                                        providerId);

                        log.info("User {} {} created/updated with ID: {}", firstName, lastName, user.getId());

                        // Ensure the authentication is properly set in the security context
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        // For incremental Gmail consent flow, redirect back to send-gmail
                        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
                                String registrationId = oauth2Token.getAuthorizedClientRegistrationId();
                                if ("google-gmail".equals(registrationId)) {
                                        String targetUrl = frontendUrl + "/send-gmail?connected=true";
                                        log.info("Redirecting to Gmail connect target: {}", targetUrl);
                                        getRedirectStrategy().sendRedirect(request, response, targetUrl);
                                        return;
                                }
                        }

                        // Decide redirect based on org membership
                        String targetUrl;
                        if (user.getOrgId() == null) {
                                targetUrl = frontendUrl + "/onboarding/organization";
                        } else {
                                targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/callback")
                                                .queryParam("success", "true")
                                                .queryParam("email", email)
                                                .build().toUriString();
                        }

                        log.info("Redirecting to: {}", targetUrl);
                        getRedirectStrategy().sendRedirect(request, response, targetUrl);

                } catch (Exception e) {
                        log.error("Error in OAuth2 success handler", e);

                        // Redirect to frontend with error
                        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/callback")
                                        .queryParam("success", "false")
                                        .queryParam("error", "Authentication failed")
                                        .build().toUriString();
                        getRedirectStrategy().sendRedirect(request, response, targetUrl);
                }
        }
}
