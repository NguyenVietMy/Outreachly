package com.outreachly.outreachly.security;

import com.outreachly.outreachly.entity.User;
import com.outreachly.outreachly.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String registrationId = getRegistrationId(request);

        log.info("OAuth2 authentication successful for registration: {}", registrationId);

        // Extract user information based on the OAuth provider
        User.AuthProvider provider = mapRegistrationIdToProvider(registrationId);
        String email = extractEmail(oauth2User, provider);
        String firstName = extractFirstName(oauth2User, provider);
        String lastName = extractLastName(oauth2User, provider);
        String profilePictureUrl = extractProfilePictureUrl(oauth2User, provider);
        String providerId = extractProviderId(oauth2User, provider);

        // Create or update user in database
        User user = userService.createOrUpdateUser(
                email, firstName, lastName, profilePictureUrl, provider, providerId);

        log.info("User processed successfully: {} (ID: {})", user.getEmail(), user.getId());

        // Redirect to frontend with success
        String targetUrl = determineTargetUrl(request, response, authentication);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String getRegistrationId(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        return requestURI.substring(requestURI.lastIndexOf('/') + 1);
    }

    private User.AuthProvider mapRegistrationIdToProvider(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> User.AuthProvider.GOOGLE;
            case "github" -> User.AuthProvider.GITHUB;
            case "microsoft" -> User.AuthProvider.MICROSOFT;
            case "facebook" -> User.AuthProvider.FACEBOOK;
            default -> throw new IllegalArgumentException("Unknown OAuth provider: " + registrationId);
        };
    }

    private String extractEmail(OAuth2User oauth2User, User.AuthProvider provider) {
        return switch (provider) {
            case GOOGLE, MICROSOFT, FACEBOOK -> oauth2User.getAttribute("email");
            case GITHUB -> oauth2User.getAttribute("email");
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    private String extractFirstName(OAuth2User oauth2User, User.AuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> oauth2User.getAttribute("given_name");
            case GITHUB ->
                oauth2User.getAttribute("name") != null ? oauth2User.getAttribute("name").toString().split(" ")[0]
                        : null;
            case MICROSOFT -> oauth2User.getAttribute("givenName");
            case FACEBOOK -> oauth2User.getAttribute("first_name");
            default -> null;
        };
    }

    private String extractLastName(OAuth2User oauth2User, User.AuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> oauth2User.getAttribute("family_name");
            case GITHUB -> {
                String name = oauth2User.getAttribute("name");
                if (name != null && name.contains(" ")) {
                    yield name.substring(name.lastIndexOf(" ") + 1);
                }
                yield null;
            }
            case MICROSOFT -> oauth2User.getAttribute("surname");
            case FACEBOOK -> oauth2User.getAttribute("last_name");
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private String extractProfilePictureUrl(OAuth2User oauth2User, User.AuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> oauth2User.getAttribute("picture");
            case GITHUB -> oauth2User.getAttribute("avatar_url");
            case MICROSOFT -> {
                Map<String, Object> photo = oauth2User.getAttribute("photo");
                if (photo != null) {
                    yield (String) photo.get("value");
                }
                yield null;
            }
            case FACEBOOK -> {
                Map<String, Object> picture = oauth2User.getAttribute("picture");
                if (picture != null) {
                    Map<String, Object> data = (Map<String, Object>) picture.get("data");
                    if (data != null) {
                        yield (String) data.get("url");
                    }
                }
                yield null;
            }
            default -> null;
        };
    }

    private String extractProviderId(OAuth2User oauth2User, User.AuthProvider provider) {
        return switch (provider) {
            case GOOGLE, MICROSOFT, FACEBOOK -> oauth2User.getAttribute("sub");
            case GITHUB -> oauth2User.getAttribute("id").toString();
            default -> throw new IllegalArgumentException("Unknown provider: " + provider);
        };
    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        // Redirect to your frontend application
        return UriComponentsBuilder.fromUriString("http://localhost:3000/auth/callback")
                .queryParam("success", "true")
                .build().toUriString();
    }
}
