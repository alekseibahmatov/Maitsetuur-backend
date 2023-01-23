package ee.kolbaska.kolbaska.service;

import ee.kolbaska.kolbaska.exception.UserAlreadyExistsException;
import ee.kolbaska.kolbaska.model.user.Role;
import ee.kolbaska.kolbaska.model.user.User;
import ee.kolbaska.kolbaska.repository.RoleRepository;
import ee.kolbaska.kolbaska.repository.UserRepository;
import ee.kolbaska.kolbaska.request.WaiterRequest;
import ee.kolbaska.kolbaska.response.WaiterResponse;
import ee.kolbaska.kolbaska.service.miscellaneous.EmailService;
import ee.kolbaska.kolbaska.service.miscellaneous.FormatService;
import ee.kolbaska.kolbaska.service.miscellaneous.PasswordService;
import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.management.relation.RoleNotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)

public class RestaurantServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private FormatService formatService;

    @Mock
    private PasswordService passwordService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RestaurantService restaurantService;


    @Test
    void testCreateWaiter() throws Exception {
        // mock the dependencies of the RestaurantService
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        User waiter = new User();
        waiter.setId(1L);
        waiter.setEmail("test@test.com");
        waiter.setFullName("John Doe");
        waiter.setPhone("+370000000");
        when(userRepository.save(any(User.class))).thenReturn(waiter);
        when(passwordService.generatePassword(anyInt())).thenReturn("password");
        when(formatService.formatE164(anyString())).thenReturn("+370000000");
        doNothing().when(emailService).sendSimpleMessage(anyString(), anyString(), anyString());
        Role role = new Role();
        role.setRoleName("ROLE_WAITER");
        when(roleRepository.findRoleByRoleName("ROLE_WAITER")).thenReturn(Optional.of(role));

        // create the request object
        WaiterRequest request = new WaiterRequest();
        request.setEmail("test@test.com");
        request.setFullName("John Doe");
        request.setPhone("0000000");

        // call the createWaiter method
        ResponseEntity<WaiterResponse> response = restaurantService.createWaiter(request);

        // assert that the response is as expected
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId().longValue());
        assertEquals("test@test.com", response.getBody().getEmail());
        assertEquals("John Doe", response.getBody().getFullName());
        assertEquals("+370000000", response.getBody().getPhone());
        assertEquals(0.0, response.getBody().getTurnover(), 0.0);

        // verify that the dependencies were called as expected
        verify(passwordEncoder).encode("password");
        verify(userRepository).findByEmail("test@test.com");
        verify(userRepository).save(any(User.class));
        verify(passwordService).generatePassword(10);
        verify(formatService).formatE164("0000000");
        verify(emailService).sendSimpleMessage("test@test.com", "Password", "Here is your password for accessing qr code page: password");
        verify(roleRepository).findRoleByRoleName("ROLE_WAITER");
    }

    @Test
    void testCreateWaiter_UserAlreadyExists() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(new User()));

        WaiterRequest request = new WaiterRequest();
        request.setEmail("test@test.com");
        request.setFullName("John Doe");
        request.setPhone("0000000");

        assertThrows(UserAlreadyExistsException.class, () -> restaurantService.createWaiter(request));
    }

    @Test
    void testCreateWaiter_RoleNotFound() {
        when(roleRepository.findRoleByRoleName("ROLE_WAITER")).thenReturn(Optional.empty());

        WaiterRequest request = new WaiterRequest();
        request.setEmail("test@test.com");
        request.setFullName("John Doe");
        request.setPhone("0000000");

        assertThrows(RoleNotFoundException.class, () -> restaurantService.createWaiter(request));
    }

    @Test
    void testCreateWaiter_InvalidPhoneNumber() {
        doThrow(IllegalArgumentException.class).when(formatService).formatE164(anyString());

        WaiterRequest request = new WaiterRequest();
        request.setEmail("test@test.com");
        request.setFullName("John Doe");
        request.setPhone("invalid_number");

        assertThrows(IllegalArgumentException.class, () -> restaurantService.createWaiter(request));
    }
}