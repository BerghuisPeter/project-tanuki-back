package io.github.peterberghuis.auth.security;

import io.github.peterberghuis.auth.entity.User;
import io.github.peterberghuis.auth.entity.UserAuthProvider;
import io.github.peterberghuis.auth.entity.UserRole;
import io.github.peterberghuis.auth.entity.UserStatus;
import io.github.peterberghuis.auth.repository.UserAuthProviderRepository;
import io.github.peterberghuis.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();

        return processOAuth2User(provider, oAuth2User);
    }

    private OAuth2User processOAuth2User(String provider, OAuth2User oAuth2User) {
        String providerUserId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");

        if (email == null) {
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }

        User user = userAuthProviderRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(UserAuthProvider::getUser)
                .orElseGet(() -> {
                    User existingUser = userRepository.findByEmail(email)
                            .orElseGet(() -> {
                                User newUser = new User();
                                newUser.setEmail(email);
                                newUser.setStatus(UserStatus.ACTIVE);
                                newUser.setRoles(Set.of(UserRole.USER));
                                return userRepository.save(newUser);
                            });

                    UserAuthProvider authProvider = new UserAuthProvider();
                    authProvider.setUser(existingUser);
                    authProvider.setProvider(provider);
                    authProvider.setProviderUserId(providerUserId);
                    userAuthProviderRepository.save(authProvider);

                    return existingUser;
                });

        return new DefaultOAuth2User(
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority(role.name()))
                        .collect(Collectors.toSet()),
                oAuth2User.getAttributes(),
                "email"
        );
    }
}
