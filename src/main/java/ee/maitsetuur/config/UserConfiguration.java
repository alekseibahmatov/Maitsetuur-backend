package ee.maitsetuur.config;

import ee.maitsetuur.model.user.Role;
import ee.maitsetuur.model.user.User;
import ee.maitsetuur.repository.UserRepository;
import ee.maitsetuur.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserConfiguration {
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Bean
    public User getRequestUser() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();

            final String jwtToken = request.getHeader("Authorization").substring(7);

            final String email = jwtService.extractUserEmail(jwtToken);

            return userRepository.findByEmail(email).orElseThrow(
                    () -> new UsernameNotFoundException("User with such email not found!")
            );
        } catch (IllegalStateException e) {
            return null;
        }
    }

    @Bean
    public List<String> getRoleNames(User user) {
        try {
            return user.getRoles().stream().map(Role::getRoleName).toList();
        } catch (NullPointerException e) {
            return null;
        }
    }
}
