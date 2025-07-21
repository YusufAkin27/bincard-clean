package akin.city_card.bus.controller;

import akin.city_card.admin.core.request.UpdateLocationRequest;
import akin.city_card.admin.exceptions.AdminNotFoundException;
import akin.city_card.bus.core.request.*;
import akin.city_card.bus.core.response.BusDTO;
import akin.city_card.bus.core.response.BusLocationDTO;
import akin.city_card.bus.core.response.BusRideDTO;
import akin.city_card.bus.core.response.StationDTO;
import akin.city_card.bus.exceptions.*;
import akin.city_card.bus.service.abstracts.BusService;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.security.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/api/bus")
@RequiredArgsConstructor
public class BusController {

    private final BusService busService;


    private boolean isAdminOrSuperAdmin(UserDetails userDetails) {
        if (userDetails == null) return false;
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ADMIN") || role.equals("SUPERADMIN"));
    }


    @GetMapping("/getAllBus")
    public DataResponseMessage<List<BusDTO>> getAllBuses(@AuthenticationPrincipal UserDetails userDetails) throws AdminNotFoundException, UnauthorizedAreaException {
        return busService.getAllBuses(userDetails.getUsername());
    }

    @GetMapping("/{busId}")
    public DataResponseMessage<BusDTO> getBusById(@PathVariable Long busId, @AuthenticationPrincipal UserDetails userDetails) throws AdminNotFoundException, BusNotFoundException, UnauthorizedAreaException {
        return busService.getBusById(busId, userDetails.getUsername());
    }

    @GetMapping("/active")
    public DataResponseMessage<List<BusDTO>> getActiveBuses(@AuthenticationPrincipal UserDetails userDetails) throws UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(userDetails)) throw new UnauthorizedAreaException();
        return busService.getActiveBuses(userDetails.getUsername());
    }

    // --- ADMIN ---

    @PostMapping("/create")
    public ResponseMessage createBus(@RequestBody CreateBusRequest request, @AuthenticationPrincipal UserDetails userDetails) throws DriverNotFoundException, AdminNotFoundException, DuplicateBusPlateException, RouteNotFoundException, UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(userDetails)) throw new UnauthorizedAreaException();
        return busService.createBus(request, userDetails.getUsername());
    }

    @PutMapping("/update/{busId}")
    public ResponseMessage updateBus(@PathVariable Long busId, @RequestBody UpdateBusRequest request, @AuthenticationPrincipal UserDetails userDetails) throws DriverNotFoundException, AdminNotFoundException, BusNotFoundException, DuplicateBusPlateException, RouteNotFoundException, UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(userDetails)) throw new UnauthorizedAreaException();

        return busService.updateBus(busId, request, userDetails.getUsername());
    }

    @DeleteMapping("/delete/{busId}")
    public ResponseMessage deleteBus(@PathVariable Long busId, @AuthenticationPrincipal UserDetails userDetails) throws AdminNotFoundException, BusNotFoundException, UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(userDetails)) throw new UnauthorizedAreaException();

        return busService.deleteBus(busId, userDetails.getUsername());
    }

    @PutMapping("/{busId}/toggle-active")
    public ResponseMessage toggleActiveStatus(@PathVariable Long busId, @AuthenticationPrincipal UserDetails userDetails) throws AdminNotFoundException, BusNotFoundException, UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(userDetails)) throw new UnauthorizedAreaException();

        return busService.toggleBusActive(busId, userDetails.getUsername());
    }

    // --- ŞOFÖR TANIMLAMA / GÜNCELLEME ---

    @PutMapping("/{busId}/assign-driver")
    public ResponseMessage assignDriverToBus(@PathVariable Long busId,
                                             @RequestBody AssignDriverRequest request,
                                             @AuthenticationPrincipal UserDetails userDetails) throws DriverNotFoundException, DriverAlreadyAssignedException, AdminNotFoundException, BusNotFoundException, UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(userDetails)) throw new UnauthorizedAreaException();

        return busService.assignDriver(busId, request.getDriverId(), userDetails.getUsername());
    }

    // --- KONUM / TAKİP / GEÇMİŞ ---

    @GetMapping("/{busId}/location")
    public DataResponseMessage<BusLocationDTO> getCurrentBusLocation(@PathVariable Long busId) throws BusNotFoundException {
        return busService.getCurrentLocation(busId);
    }

    @PostMapping("/{busId}/location")
    public ResponseMessage updateBusLocation(@PathVariable Long busId,
                                             @RequestBody UpdateLocationRequest request) throws UnauthorizedLocationUpdateException, BusNotFoundException {
        return busService.updateLocation(busId, request);
    }

    @GetMapping("/{busId}/location-history")
    public DataResponseMessage<List<BusLocationDTO>> getLocationHistory(
            @PathVariable Long busId,
            @RequestParam(required = false) LocalDate date,
            @AuthenticationPrincipal UserDetails userDetails) throws UnauthorizedAccessException, BusNotFoundException, UnauthorizedAreaException {
        if (!isAdminOrSuperAdmin(userDetails)) throw new UnauthorizedAreaException();

        return busService.getLocationHistory(busId, date, userDetails.getUsername());
    }


    // --- ROTA / DURAKLAR / MESAFE ---

    @PutMapping("/{busId}/route")    //kontrol edilecek
    public ResponseMessage assignRouteToBus(@PathVariable Long busId,
                                            @RequestBody AssignRouteRequest request,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        return busService.assignRoute(busId, request.getRouteId(), userDetails.getUsername());
    }

    @GetMapping("/{busId}/route/stations")     //kontrol edilecek
    public DataResponseMessage<List<StationDTO>> getRouteStations(@PathVariable Long busId,
                                                                  @AuthenticationPrincipal UserDetails userDetails) {
        return busService.getRouteStations(busId, userDetails.getUsername());
    }

    @GetMapping("/{busId}/eta")           //kontrol edilecek
    public DataResponseMessage<Double> getEstimatedTimeToStation(@PathVariable Long busId,
                                                                 @RequestParam Long stationId,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        return busService.getEstimatedArrivalTime(busId, stationId, userDetails.getUsername());
    }
}
