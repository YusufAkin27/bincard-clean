package akin.city_card.route.controller;

import akin.city_card.bus.exceptions.RouteNotFoundException;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;
import akin.city_card.route.core.request.CreateRouteRequest;
import akin.city_card.route.core.request.UpdateRouteRequest;
import akin.city_card.route.core.response.RouteDTO;
import akin.city_card.route.model.Route;
import akin.city_card.route.service.abstracts.RouteService;
import akin.city_card.station.exceptions.StationNotFoundException;
import akin.city_card.user.core.response.Views;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/api/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @JsonView(Views.Public.class)
    @GetMapping("/getAllRoutes")
    public DataResponseMessage<List<RouteDTO>> getAllRoutes() {
        return routeService.getAllRoutes();
    }

    @JsonView(Views.Public.class)
    @GetMapping("/{id}")
    public DataResponseMessage<RouteDTO> getRouteById(@PathVariable Long id) throws RouteNotFoundException {
        return routeService.getRouteById(id);
    }

    @JsonView(Views.Public.class)
    @GetMapping("/search-by-name")
    public DataResponseMessage<List<RouteDTO>> searchRoutesByName(@RequestParam String name) {
        return routeService.searchRoutesByName(name);
    }

    @JsonView(Views.User.class)
    @GetMapping("/search-by-station")
    public DataResponseMessage<List<RouteDTO>> searchRoutesByStationId(@RequestParam Long stationId)
            throws StationNotFoundException {
        return routeService.findRoutesByStationId(stationId);
    }

    @PostMapping("/create-route")
    public ResponseMessage createRoute(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestBody CreateRouteRequest request)
            throws StationNotFoundException, UnauthorizedAreaException {
        return routeService.createRoute(userDetails.getUsername(), request);
    }

    @JsonView(Views.Admin.class)
    @PutMapping("/{id}")
    public DataResponseMessage<RouteDTO> updateRoute(@AuthenticationPrincipal UserDetails userDetails,
                                                     @RequestBody UpdateRouteRequest request)
            throws StationNotFoundException, UnauthorizedAreaException, RouteNotFoundException {
        return routeService.updateRoute(userDetails.getUsername(), request);
    }

    @DeleteMapping("/{id}")
    public ResponseMessage deleteRoute(@AuthenticationPrincipal UserDetails userDetails,
                                       @PathVariable Long id)
            throws UnauthorizedAreaException, RouteNotFoundException {
        return routeService.deleteRoute(userDetails.getUsername(), id);
    }

    @JsonView(Views.Admin.class)
    @PostMapping("/{routeId}/add-station")
    public DataResponseMessage<RouteDTO> addStationToRoute(@AuthenticationPrincipal UserDetails userDetails,
                                                           @PathVariable Long routeId,
                                                           @RequestParam Long afterStationId,
                                                           @RequestParam Long newStationId)
            throws StationNotFoundException, RouteNotFoundException {
        return routeService.addStationToRoute(routeId, afterStationId, newStationId, userDetails.getUsername());
    }

    @JsonView(Views.Admin.class)
    @DeleteMapping("/{routeId}/remove-station")
    public DataResponseMessage<RouteDTO> removeStationFromRoute(@AuthenticationPrincipal UserDetails userDetails,
                                                                @PathVariable Long routeId,
                                                                @RequestParam Long stationId)
            throws StationNotFoundException, RouteNotFoundException {
        return routeService.removeStationFromRoute(routeId, stationId, userDetails.getUsername());
    }
}


