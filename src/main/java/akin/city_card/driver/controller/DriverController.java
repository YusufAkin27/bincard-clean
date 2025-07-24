package akin.city_card.driver.controller;

import akin.city_card.driver.core.request.CreateDriverRequest;
import akin.city_card.driver.core.request.UpdateDriverRequest;
import akin.city_card.driver.core.response.DriverDocumentDto;
import akin.city_card.driver.core.response.DriverDto;
import akin.city_card.driver.core.response.DriverPenaltyDto;
import akin.city_card.driver.core.response.DriverPerformanceDto;
import akin.city_card.driver.service.absracts.DriverService;
import akin.city_card.news.core.response.PageDTO;
import akin.city_card.response.DataResponseMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    // === DRIVER CRUD ===

    @PostMapping
    public DataResponseMessage<DriverDto> createDriver(@RequestBody CreateDriverRequest request,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.createDriver(request, userDetails.getUsername());
    }

    @PutMapping("/{id}")
    public DataResponseMessage<DriverDto> updateDriver(@PathVariable Long id,
                                                       @RequestBody UpdateDriverRequest dto,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.updateDriver(id, dto, userDetails.getUsername());
    }

    @DeleteMapping("/{id}")
    public DataResponseMessage<Void> deleteDriver(@PathVariable Long id,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.deleteDriver(id, userDetails.getUsername());
    }

    @GetMapping("/{id}")
    public DataResponseMessage<DriverDto> getDriverById(@PathVariable Long id,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.getDriverById(id, userDetails.getUsername());
    }

    @GetMapping
    public DataResponseMessage<PageDTO<DriverDto>> getAllDrivers(@RequestParam(required = false)int page,
                                                                 @RequestParam(required = false)int size,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.getAllDrivers(page,size, userDetails.getUsername());
    }

    // === DOCUMENTS ===

    @GetMapping("/{id}/documents")
    public DataResponseMessage<PageDTO<DriverDocumentDto>> getDriverDocuments(@PathVariable Long id,
                                                                              Pageable pageable,
                                                                              @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.getDriverDocuments(id, pageable, userDetails.getUsername());
    }

    @PostMapping("/{id}/documents")
    public DataResponseMessage<DriverDocumentDto> addDriverDocument(@PathVariable Long id,
                                                                    @RequestBody DriverDocumentDto dto,
                                                                    @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.addDriverDocument(id, dto, userDetails.getUsername());
    }

    @PutMapping("/documents/{docId}")
    public DataResponseMessage<DriverDocumentDto> updateDriverDocument(@PathVariable Long docId,
                                                                       @RequestBody DriverDocumentDto dto,
                                                                       @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.updateDriverDocument(docId, dto, userDetails.getUsername());
    }

    @DeleteMapping("/documents/{docId}")
    public DataResponseMessage<Void> deleteDriverDocument(@PathVariable Long docId,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.deleteDriverDocument(docId, userDetails.getUsername());
    }

    // === PENALTIES ===

    @GetMapping("/{id}/penalties")
    public DataResponseMessage<PageDTO<DriverPenaltyDto>> getDriverPenalties(@PathVariable Long id,
                                                                             Pageable pageable,
                                                                             @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.getDriverPenalties(id, pageable, userDetails.getUsername());
    }

    @PostMapping("/{id}/penalties")
    public DataResponseMessage<DriverPenaltyDto> addDriverPenalty(@PathVariable Long id,
                                                                  @RequestBody DriverPenaltyDto dto,
                                                                  @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.addDriverPenalty(id, dto, userDetails.getUsername());
    }

    @PutMapping("/penalties/{penaltyId}")
    public DataResponseMessage<DriverPenaltyDto> updateDriverPenalty(@PathVariable Long penaltyId,
                                                                     @RequestBody DriverPenaltyDto dto,
                                                                     @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.updateDriverPenalty(penaltyId, dto, userDetails.getUsername());
    }

    @DeleteMapping("/penalties/{penaltyId}")
    public DataResponseMessage<Void> deleteDriverPenalty(@PathVariable Long penaltyId,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.deleteDriverPenalty(penaltyId, userDetails.getUsername());
    }

    // === PERFORMANCE ===

    @GetMapping("/{id}/performance")
    public DataResponseMessage<DriverPerformanceDto> getDriverPerformance(@PathVariable Long id,
                                                                          @AuthenticationPrincipal UserDetails userDetails) {
        return driverService.getDriverPerformance(id, userDetails.getUsername());
    }
}

