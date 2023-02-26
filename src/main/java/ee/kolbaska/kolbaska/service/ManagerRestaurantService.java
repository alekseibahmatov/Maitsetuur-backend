package ee.kolbaska.kolbaska.service;

import ee.kolbaska.kolbaska.config.UserConfiguration;
import ee.kolbaska.kolbaska.exception.RestaurantNotFoundException;
import ee.kolbaska.kolbaska.exception.UserStillOnDutyExceptions;
import ee.kolbaska.kolbaska.model.restaurant.Restaurant;
import ee.kolbaska.kolbaska.model.transaction.Transaction;
import ee.kolbaska.kolbaska.model.user.User;
import ee.kolbaska.kolbaska.repository.RestaurantRepository;
import ee.kolbaska.kolbaska.repository.RoleRepository;
import ee.kolbaska.kolbaska.repository.UserRepository;
import ee.kolbaska.kolbaska.request.WaiterRequest;
import ee.kolbaska.kolbaska.response.WaiterDeletedResponse;
import ee.kolbaska.kolbaska.response.WaiterResponse;
import ee.kolbaska.kolbaska.service.miscellaneous.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.management.relation.RoleNotFoundException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ManagerRestaurantService {

    private final UserRepository userRepository;

    private final EmailService emailService;

    private final RoleRepository roleRepository;

    private final RestaurantRepository restaurantRepository;

    private final UserConfiguration userConfiguration;

    @Transactional
    public WaiterResponse createWaiter(WaiterRequest request) throws Exception {

        User manager = userConfiguration.getRequestUser();

        if (manager.getRestaurant() == null) throw new RestaurantNotFoundException("This manager is not assigned to any restaurant");

        Optional<User> userExists = userRepository.findByEmail(request.getEmail());

        Restaurant restaurant = manager.getRestaurant();

        User waiter;

        if (userExists.isEmpty()) {

            String activationCode = UUID.randomUUID().toString();

            waiter = User.builder()
                    .email(request.getEmail())
                    .roles(List.of(roleRepository.findRoleByRoleName("ROLE_WAITER").orElseThrow(RoleNotFoundException::new), roleRepository.findRoleByRoleName("ROLE_NEWBIE").orElseThrow(RoleNotFoundException::new)))
                    .activationCode(activationCode)
                    .activated(true)
                    .deleted(false)
                    .restaurant(restaurant)
                    .build();

            emailService.sendSimpleMessage(request.getEmail(), "Activate your account", String.format("Here is your uuid to activate you account: %s", activationCode));


            waiter = userRepository.save(waiter);

        } else {
            waiter = userExists.get();

            if (waiter.getRestaurant() != null) throw new UserStillOnDutyExceptions("User is currently connected to restaurant. Please ask him to disconnect from previous restaurant");

            waiter.setRestaurant(restaurant);
        }

        if(restaurant.getWaiters() == null) restaurant.setWaiters(List.of(waiter));
        else restaurant.getWaiters().add(waiter);

        restaurantRepository.save(restaurant);

        return WaiterResponse.builder()
                .id(waiter.getId())
                .phone(waiter.getPhone())
                .turnover(0.0)
                .email(waiter.getEmail())
                .fullName(waiter.getFullName())
                .build();

    }

    public WaiterDeletedResponse deleteWaiter(Long id) {
        Optional<User> waiterExists = userRepository.findById(id);

        if (waiterExists.isEmpty()) throw new UsernameNotFoundException(String.format("User with id: %x not found", id));

        User waiter = waiterExists.get();

        waiter.setDeleted(true);
        waiter.setDeletedAt(new Date());

        Restaurant restaurant = waiter.getRestaurant();
        restaurant.setWaiters(restaurant.getWaiters().stream().filter((rest) -> !Objects.equals(rest.getId(), waiter.getId())).toList());

        waiter.setRestaurant(null);

        userRepository.save(waiter);
        restaurantRepository.save(restaurant);

        return WaiterDeletedResponse.builder()
                .id(id)
                .deleted(true)
                .build();
    }

    public List<WaiterResponse> getWaiters() throws RestaurantNotFoundException {

        User manager = userConfiguration.getRequestUser();

        Restaurant restaurant = manager.getRestaurant();

        if (restaurant == null) throw new RestaurantNotFoundException("This manager is not associated with any restaurant!");

        List<User> usersWithoutManager = restaurant.getWaiters().stream()
                .filter(user -> !Objects.equals(user.getEmail(), manager.getEmail())).toList();

        List<WaiterResponse> response = new ArrayList<>();

        for (User u: usersWithoutManager) {
            double turnover = 0.0;

            for (Transaction t: u.getTransactions()) {
                turnover += t.getValue();
            }

            WaiterResponse waiterResponse = WaiterResponse.builder()
                    .id(u.getId())
                    .fullName(u.getFullName())
                    .phone(u.getPhone())
                    .email(u.getEmail())
                    .turnover(turnover)
                    .build();

            response.add(waiterResponse);
        }

        return response;
    }
}
