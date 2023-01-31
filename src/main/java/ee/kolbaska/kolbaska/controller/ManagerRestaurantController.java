package ee.kolbaska.kolbaska.controller;

import ee.kolbaska.kolbaska.request.RestaurantRequest;
import ee.kolbaska.kolbaska.request.WaiterRequest;
import ee.kolbaska.kolbaska.response.RestaurantResponse;
import ee.kolbaska.kolbaska.response.WaiterDeletedResponse;
import ee.kolbaska.kolbaska.response.WaiterResponse;
import ee.kolbaska.kolbaska.service.ManagerRestaurantService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("${api.basepath}/manager")
@RequiredArgsConstructor
public class ManagerRestaurantController {

    private final ManagerRestaurantService service;

    @PostMapping("/restaurant/waiter")
    public ResponseEntity<WaiterResponse> createWaiter(
            @NotNull @RequestBody WaiterRequest request
            ) throws Exception {
        return service.createWaiter(request);
    }

    @DeleteMapping("/restaurant/waiter/{id}")
    public ResponseEntity<WaiterDeletedResponse> deleteWaiter(
            @NotNull @PathVariable Long id
    ) {
        return service.deleteWaiter(id);
    }
}
