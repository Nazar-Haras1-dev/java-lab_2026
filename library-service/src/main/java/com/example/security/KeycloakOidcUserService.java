package com.example.library.security;

import com.nimbusds.jwt.JWTParser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Після OIDC-логіну додає ролі Keycloak (з access token) до користувача UI-сесії. */
@Component
public class KeycloakOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);

        Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
        authorities.addAll(rolesFromAccessToken(userRequest.getAccessToken().getTokenValue()));

        return new DefaultOidcUser(authorities, oidcUser.getIdToken(),
                oidcUser.getUserInfo(), "preferred_username");
    }

    private Collection<GrantedAuthority> rolesFromAccessToken(String token) {
        try {
            Map<String, Object> realmAccess =
                    JWTParser.parse(token).getJWTClaimsSet().getJSONObjectClaim("realm_access");
            return KeycloakRoleConverter.extractAuthorities(realmAccess);
        } catch (ParseException e) {
            return List.of();
        }
    }
}