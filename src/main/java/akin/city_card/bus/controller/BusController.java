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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/v1/api/bus")
@RequiredArgsConstructor
@Slf4j
public class BusController {

    private final BusService busService;

    private boolean isAdminOrSuperAdmin(UserDetails userDetails) {
        if (userDetails == null) return false;
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ADMIN") || role.equals("SUPERADMIN"));
    }

    // === GENEL SORGULAMA ENDPOİNTLERİ ===

    @GetMapping("/all")
    public ResponseEntity<DataResponseMessage<List<BusDTO>>> getAllBuses(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            DataResponseMessage<List<BusDTO>> response = busService.getAllBuses(userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (AdminNotFoundException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new DataResponseMessage<>("Yetkisiz erişim.", false, null));
        } catch (UnauthorizedAreaException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new DataResponseMessage<>("Bu alana erişim yetkiniz yok.", false, null));
        } catch (Exception e) {
            log.error("Error getting all buses: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Sistem hatası oluştu.", false, null));
        }
    }

    @GetMapping("/{busId}")
    public ResponseEntity<DataResponseMessage<BusDTO>> getBusById(
            @PathVariable Long busId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            DataResponseMessage<BusDTO> response = busService.getBusById(busId, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new DataResponseMessage<>("Otobüs bulunamadı.", false, null));
        } catch (UnauthorizedAreaException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new DataResponseMessage<>("Bu alana erişim yetkiniz yok.", false, null));
        } catch (Exception e) {
            log.error("Error getting bus by ID: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Sistem hatası oluştu.", false, null));
        }
    }

    @GetMapping("/active")
    public ResponseEntity<DataResponseMessage<List<BusDTO>>> getActiveBuses(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new DataResponseMessage<>("Yetkisiz erişim.", false, null));
            }

            DataResponseMessage<List<BusDTO>> response = busService.getActiveBuses(userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting active buses: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Sistem hatası oluştu.", false, null));
        }
    }

    // === CRUD İŞLEMLERİ ===

    @PostMapping("/create")
    public ResponseEntity<ResponseMessage> createBus(
            @Valid @RequestBody CreateBusRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.createBus(request, userDetails.getUsername());
            return ResponseEntity.status(response.isSuccess() ? HttpStatus.CREATED : HttpStatus.BAD_REQUEST)
                    .body(response);
        } catch (DuplicateBusPlateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResponseMessage("Bu plaka zaten sistemde kayıtlı.", false));
        } catch (RouteNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Belirtilen rota bulunamadı.", false));
        } catch (DriverNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Belirtilen şoför bulunamadı.", false));
        } catch (Exception e) {
            log.error("Error creating bus: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Otobüs oluşturulurken hata oluştu.", false));
        }
    }

    @PutMapping("/update/{busId}")
    public ResponseEntity<ResponseMessage> updateBus(
            @PathVariable Long busId,
            @Valid @RequestBody UpdateBusRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.updateBus(busId, request, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Otobüs bulunamadı.", false));
        } catch (DuplicateBusPlateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResponseMessage("Bu plaka zaten başka bir otobüste kayıtlı.", false));
        } catch (DriverNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Belirtilen şoför bulunamadı.", false));
        } catch (RouteNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Belirtilen rota bulunamadı.", false));
        } catch (Exception e) {
            log.error("Error updating bus: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Otobüs güncellenirken hata oluştu.", false));
        }
    }

    @DeleteMapping("/delete/{busId}")
    public ResponseEntity<ResponseMessage> deleteBus(
            @PathVariable Long busId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.deleteBus(busId, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Otobüs bulunamadı.", false));
        } catch (Exception e) {
            log.error("Error deleting bus: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Otobüs silinirken hata oluştu.", false));
        }
    }

    @PutMapping("/{busId}/toggle-active")
    public ResponseEntity<ResponseMessage> toggleActiveStatus(
            @PathVariable Long busId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.toggleBusActive(busId, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Otobüs bulunamadı.", false));
        } catch (Exception e) {
            log.error("Error toggling bus active status: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Otobüs durumu değiştirilirken hata oluştu.", false));
        }
    }

    // === ŞOFÖR YÖNETİMİ ===

    @PutMapping("/{busId}/assign-driver")
    public ResponseEntity<ResponseMessage> assignDriverToBus(
            @PathVariable Long busId,
            @Valid @RequestBody AssignDriverRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.assignDriver(busId, request.getDriverId(), userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Otobüs bulunamadı.", false));
        } catch (DriverNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Şoför bulunamadı.", false));
        } catch (DriverAlreadyAssignedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ResponseMessage("Şoför zaten başka bir otobüse atanmış.", false));
        } catch (Exception e) {
            log.error("Error assigning driver to bus: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Şoför ataması yapılırken hata oluştu.", false));
        }
    }

    // === KONUM YÖNETİMİ ===

    @GetMapping("/{busId}/location")
    public ResponseEntity<DataResponseMessage<BusLocationDTO>> getCurrentBusLocation(@PathVariable Long busId) {
        try {
            DataResponseMessage<BusLocationDTO> response = busService.getCurrentLocation(busId);
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new DataResponseMessage<>("Otobüs bulunamadı.", false, null));
        } catch (Exception e) {
            log.error("Error getting current bus location: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Konum bilgisi alınırken hata oluştu.", false, null));
        }
    }

    @PostMapping("/{busId}/location")
    public ResponseEntity<ResponseMessage> updateBusLocation(
            @PathVariable Long busId,
            @Valid @RequestBody UpdateLocationRequest request) {
        try {
            ResponseMessage response = busService.updateLocation(busId, request);
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Otobüs bulunamadı.", false));
        } catch (UnauthorizedLocationUpdateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ResponseMessage("Konum güncelleme yetkisi yok.", false));
        } catch (Exception e) {
            log.error("Error updating bus location: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Konum güncellenirken hata oluştu.", false));
        }
    }

    @GetMapping("/{busId}/location-history")
    public ResponseEntity<DataResponseMessage<List<BusLocationDTO>>> getLocationHistory(
            @PathVariable Long busId,
            @RequestParam(required = false) LocalDate date,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new DataResponseMessage<>("Yetkisiz erişim.", false, null));
            }

            DataResponseMessage<List<BusLocationDTO>> response = busService.getLocationHistory(busId, date, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new DataResponseMessage<>("Otobüs bulunamadı.", false, null));
        } catch (UnauthorizedAccessException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new DataResponseMessage<>("Bu bilgilere erişim yetkiniz yok.", false, null));
        } catch (Exception e) {
            log.error("Error getting location history: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Konum geçmişi alınırken hata oluştu.", false, null));
        }
    }

    // === ROTA YÖNETİMİ ===

    @PutMapping("/{busId}/route")
    public ResponseEntity<ResponseMessage> assignRouteToBus(
            @PathVariable Long busId,
            @Valid @RequestBody AssignRouteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.assignRoute(busId, request.getRouteId(), userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error assigning route to bus: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Rota ataması yapılırken hata oluştu.", false));
        }
    }

    @GetMapping("/{busId}/route/stations")
    public ResponseEntity<DataResponseMessage<List<StationDTO>>> getRouteStations(
            @PathVariable Long busId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            DataResponseMessage<List<StationDTO>> response = busService.getRouteStations(busId, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting route stations: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Rota istasyonları alınırken hata oluştu.", false, null));
        }
    }

    @GetMapping("/{busId}/eta")
    public ResponseEntity<DataResponseMessage<Double>> getEstimatedTimeToStation(
            @PathVariable Long busId,
            @RequestParam Long stationId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            DataResponseMessage<Double> response = busService.getEstimatedArrivalTime(busId, stationId, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting estimated arrival time: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Tahmini varış süresi hesaplanırken hata oluştu.", false, null));
        }
    }

    // === YÖN YÖNETİMİ ===

    @PutMapping("/{busId}/switch-direction")
    public ResponseEntity<ResponseMessage> switchBusDirection(
            @PathVariable Long busId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.switchDirection(busId, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Otobüs bulunamadı.", false));
        } catch (Exception e) {
            log.error("Error switching bus direction: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Yön değiştirilirken hata oluştu.", false));
        }
    }

    // === İSTATİSTİKLER ===

    @GetMapping("/statistics")
    public ResponseEntity<DataResponseMessage<Object>> getBusStatistics(
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new DataResponseMessage<>("Yetkisiz erişim.", false, null));
            }

            DataResponseMessage<Object> response = busService.getBusStatistics(userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting bus statistics: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("İstatistikler alınırken hata oluştu.", false, null));
        }
    }

    // === ARAMA VE FİLTRELEME ===

    @GetMapping("/search")
    public ResponseEntity<DataResponseMessage<List<BusDTO>>> searchBuses(
            @RequestParam(required = false) String numberPlate,
            @RequestParam(required = false) Long routeId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new DataResponseMessage<>("Yetkisiz erişim.", false, null));
            }

            DataResponseMessage<List<BusDTO>> response;

            if (numberPlate != null && !numberPlate.trim().isEmpty()) {
                response = busService.searchByNumberPlate(numberPlate, userDetails.getUsername());
            } else if (routeId != null) {
                response = busService.getBusesByRoute(routeId, userDetails.getUsername());
            } else if (driverId != null) {
                response = busService.getBusesByDriver(driverId, userDetails.getUsername());
            } else if (status != null) {
                response = busService.getBusesByStatus(status, userDetails.getUsername());
            } else {
                response = busService.getAllBuses(userDetails.getUsername());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error searching buses: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Arama yapılırken hata oluştu.", false, null));
        }
    }

    @GetMapping("/route/{routeId}")
    public ResponseEntity<DataResponseMessage<List<BusDTO>>> getBusesByRoute(
            @PathVariable Long routeId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            DataResponseMessage<List<BusDTO>> response = busService.getBusesByRoute(routeId, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting buses by route: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Rotadaki otobüsler getirilirken hata oluştu.", false, null));
        }
    }

    @GetMapping("/driver/{driverId}")
    public ResponseEntity<DataResponseMessage<List<BusDTO>>> getBusesByDriver(
            @PathVariable Long driverId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            DataResponseMessage<List<BusDTO>> response = busService.getBusesByDriver(driverId, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting buses by driver: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Şoförün otobüsleri getirilirken hata oluştu.", false, null));
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<DataResponseMessage<List<BusDTO>>> getBusesByStatus(
            @PathVariable String status,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            DataResponseMessage<List<BusDTO>> response = busService.getBusesByStatus(status, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting buses by status: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DataResponseMessage<>("Durumdaki otobüsler getirilirken hata oluştu.", false, null));
        }
    }

    // === DURUM YÖNETİMİ ===

    @PutMapping("/{busId}/status")
    public ResponseEntity<ResponseMessage> updateBusStatus(
            @PathVariable Long busId,
            @Valid @RequestBody BusStatusUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.updateBusStatus(busId, request, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Otobüs bulunamadı.", false));
        } catch (Exception e) {
            log.error("Error updating bus status: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Durum güncellenirken hata oluştu.", false));
        }
    }

    // === YOLCU YÖNETİMİ ===

    @PutMapping("/{busId}/passenger-count")
    public ResponseEntity<ResponseMessage> updatePassengerCount(
            @PathVariable Long busId,
            @RequestParam Integer count,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.updatePassengerCount(busId, count, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (BusNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseMessage("Otobüs bulunamadı.", false));
        } catch (Exception e) {
            log.error("Error updating passenger count: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Yolcu sayısı güncellenirken hata oluştu.", false));
        }
    }

    // === TOPLU İŞLEMLER ===

    @PutMapping("/bulk/activate")
    public ResponseEntity<ResponseMessage> bulkActivateBuses(
            @RequestBody List<Long> busIds,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.bulkActivate(busIds, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error bulk activating buses: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Toplu aktifleştirme sırasında hata oluştu.", false));
        }
    }

    @PutMapping("/bulk/deactivate")
    public ResponseEntity<ResponseMessage> bulkDeactivateBuses(
            @RequestBody List<Long> busIds,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (!isAdminOrSuperAdmin(userDetails)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ResponseMessage("Yetkisiz erişim.", false));
            }

            ResponseMessage response = busService.bulkDeactivate(busIds, userDetails.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error bulk deactivating buses: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseMessage("Toplu pasifleştirme sırasında hata oluştu.", false));
        }
    }

    // === GLOBAL HATA YÖNETİMİ ===

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ResponseMessage> handleValidationException(
            jakarta.validation.ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("Geçersiz veri girişi.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseMessage(message, false));
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseMessage> handleMethodArgumentNotValid(
            org.springframework.web.bind.MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Geçersiz veri girişi.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ResponseMessage(message, false));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseMessage> handleGenericException(Exception e) {
        log.error("Unexpected error in BusController: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ResponseMessage("Beklenmeyen bir hata oluştu.", false));
    }
}