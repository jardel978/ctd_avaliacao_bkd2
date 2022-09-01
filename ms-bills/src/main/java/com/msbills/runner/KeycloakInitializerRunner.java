package com.msbills.runner;

import com.msbills.config.security.WebSecurityConfig;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class KeycloakInitializerRunner implements CommandLineRunner {

    @Autowired
    private final Keycloak keycloakAdmin;

    private static final String KEYCLOAK_SERVER_URL = "http://localhost:8080";
    private static final String COMPANY_SERVICE_REALM_NAME = "umReino";
    private static final String MS_GATEWAY_CLIENT_ID = "spring-gateway-client";
    private static final String MS_GATEWAY_REDIRECT_URL = "http://localhost:8090/*";
    private static final List<UserPass> MS_GATEWAY_USERS = Arrays.asList(
            new UserPass("admin", "admin"),
            new UserPass("user", "user"));

    @Override
    public void run(String... args) {
        log.info("\n\nInitializing '{}' realm in Keycloak ...\n\n", COMPANY_SERVICE_REALM_NAME);
        try {
            Optional<RealmRepresentation> representationOptional = keycloakAdmin.realms()
                    .findAll()
                    .stream()
                    .filter(r -> r.getRealm().equals(COMPANY_SERVICE_REALM_NAME))
                    .findAny();
            if (representationOptional.isPresent()) {
                log.info("\nRemoving already pre-configured '{}' realm\n", COMPANY_SERVICE_REALM_NAME);
                keycloakAdmin.realm(COMPANY_SERVICE_REALM_NAME).remove();
            }

            if (representationOptional.isPresent()) {
                log.info("\nRemoving already pre-configured '{}' realm\n", COMPANY_SERVICE_REALM_NAME);
                keycloakAdmin.realm(COMPANY_SERVICE_REALM_NAME).remove();
                log.info("\nRealm '{}' removed!\n", COMPANY_SERVICE_REALM_NAME);
            }

            // Realm
            RealmRepresentation realmRepresentation = new RealmRepresentation();
            realmRepresentation.setRealm(COMPANY_SERVICE_REALM_NAME);
            realmRepresentation.setEnabled(true);
            realmRepresentation.setRegistrationAllowed(true);

            // Client
            ClientRepresentation clientRepresentation = new ClientRepresentation();
            clientRepresentation.setClientId(MS_GATEWAY_CLIENT_ID);
            clientRepresentation.setDirectAccessGrantsEnabled(true);
            clientRepresentation.setPublicClient(true);
//            clientRepresentation.setSecret("");
            clientRepresentation.setRedirectUris(Collections.singletonList(MS_GATEWAY_REDIRECT_URL));
            clientRepresentation.setBaseUrl(MS_GATEWAY_REDIRECT_URL);
            clientRepresentation.setDefaultRoles(new String[]{WebSecurityConfig.USER, WebSecurityConfig.ADMIN});
            log.info("DefaultRoles", clientRepresentation.getDefaultRoles());
            realmRepresentation.setClients(Collections.singletonList(clientRepresentation));

            // Users
            List<UserRepresentation> userRepresentations = MS_GATEWAY_USERS.stream()
                    .map(userPass -> {
                        // User Credentials
                        CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
                        credentialRepresentation.setType(CredentialRepresentation.PASSWORD);
                        credentialRepresentation.setValue(userPass.getPassword());

                        // User
                        UserRepresentation userRepresentation = new UserRepresentation();
                        userRepresentation.setUsername(userPass.getUsername());
                        userRepresentation.setEnabled(true);
                        userRepresentation.setCredentials(Collections.singletonList(credentialRepresentation));
                        userRepresentation.setClientRoles(getClientRoles(userPass));

                        return userRepresentation;
                    })
                    .collect(Collectors.toList());
            realmRepresentation.setUsers(userRepresentations);

            // Create Realm
            keycloakAdmin.realms().create(realmRepresentation);

            // Testing
            UserPass admin = MS_GATEWAY_USERS.get(0);
            log.info("Testing getting token for '{}' ...", admin.getUsername());

            Keycloak keycloakGatewayApp = KeycloakBuilder.builder().serverUrl(KEYCLOAK_SERVER_URL)
                    .realm(COMPANY_SERVICE_REALM_NAME).username(admin.getUsername()).password(admin.getPassword())
                    .clientId(MS_GATEWAY_CLIENT_ID).build();

            log.info("\n'{}' token: {}\n", admin.getUsername(), keycloakGatewayApp.tokenManager().grantToken().getToken());
            log.info("\n'{}' initialization completed successfully!\n", COMPANY_SERVICE_REALM_NAME);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private Map<String, List<String>> getClientRoles(UserPass userPass) {
        List<String> roles = new ArrayList<>();
        roles.add(WebSecurityConfig.USER);
        if ("admin".equals(userPass.getUsername())) {
            roles.add(WebSecurityConfig.ADMIN);
            roles.add(WebSecurityConfig.USER);
        }
        if ("user1".equals(userPass.getUsername())) {
            roles.add(WebSecurityConfig.USER);
        }
        return Map.of(MS_GATEWAY_CLIENT_ID, roles);
    }

    @Value
    private static class UserPass {
        String username;
        String password;
    }

}