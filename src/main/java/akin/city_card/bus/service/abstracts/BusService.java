package akin.city_card.bus.service.abstracts;

import akin.city_card.admin.core.request.UpdateLocationRequest;
import akin.city_card.admin.exceptions.AdminNotFoundException;
import akin.city_card.bus.core.request.BusStatusUpdateRequest;
import akin.city_card.bus.core.request.CreateBusRequest;
import akin.city_card.bus.core.request.UpdateBusRequest;
import akin.city_card.bus.core.response.BusDTO;
import akin.city_card.bus.core.response.BusLocationDTO;
import akin.city_card.bus.core.response.StationDTO;
import akin.city_card.bus.exceptions.*;
import akin.city_card.news.core.response.PageDTO;
import akin.city_card.news.exceptions.UnauthorizedAreaException;
import akin.city_card.response.DataResponseMessage;
import akin.city_card.response.ResponseMessage;

import java.time.LocalDate;
import java.util.List;

public interface BusService {

    // === TEMEL CRUD İŞLEMLERİ ===

    /**
     * Tüm otobüsleri listele
     */
    DataResponseMessage<PageDTO<BusDTO>> getAllBuses(String username, int page, int size)
            throws AdminNotFoundException, UnauthorizedAreaException;

    DataResponseMessage<PageDTO<BusDTO>> getBusesByStatus(String status, String username, int page, int size);
    /**
     * ID'ye göre otobüs getir
     */
    DataResponseMessage<BusDTO> getBusById(Long busId, String username)
            throws AdminNotFoundException, BusNotFoundException, UnauthorizedAreaException;

    /**
     * Aktif otobüsleri listele
     */
    DataResponseMessage<PageDTO<BusDTO>> getActiveBuses(String username, int page, int size);

    /**
     * Yeni otobüs oluştur
     */
    ResponseMessage createBus(CreateBusRequest request, String username)
            throws AdminNotFoundException, DuplicateBusPlateException, RouteNotFoundException, DriverNotFoundException, DriverInactiveException, DriverAlreadyAssignedToBusException, BusAlreadyAssignedAnotherDriverException;

    /**
     * Otobüs bilgilerini güncelle
     */
    ResponseMessage updateBus(Long busId, UpdateBusRequest request, String username)
            throws AdminNotFoundException, DuplicateBusPlateException, DriverNotFoundException,
            RouteNotFoundException, BusNotFoundException;

    /**
     * Otobüsü sil (soft delete)
     */
    ResponseMessage deleteBus(Long busId, String username)
            throws AdminNotFoundException, BusNotFoundException;

    /**
     * Otobüs aktiflik durumunu değiştir
     */
    ResponseMessage toggleBusActive(Long busId, String username)
            throws AdminNotFoundException, BusNotFoundException;

    // === ŞOFÖR YÖNETİMİ ===

    /**
     * Otobüse şoför ata
     */
    ResponseMessage assignDriver(Long busId, Long driverId, String username)
            throws AdminNotFoundException, BusNotFoundException, DriverNotFoundException, DriverAlreadyAssignedException;

    // === KONUM YÖNETİMİ ===

    /**
     * Otobüsün güncel konumunu getir
     */
    DataResponseMessage<BusLocationDTO> getCurrentLocation(Long busId) throws BusNotFoundException;

    /**
     * Otobüs konumunu güncelle
     */
    ResponseMessage updateLocation(Long busId, UpdateLocationRequest request)
            throws UnauthorizedLocationUpdateException, BusNotFoundException;

    /**
     * Konum geçmişini getir
     */
    DataResponseMessage<List<BusLocationDTO>> getLocationHistory(Long busId, LocalDate date, String username)
            throws UnauthorizedAccessException, BusNotFoundException;

    // === ROTA YÖNETİMİ ===

    /**
     * Otobüse rota ata
     */
    ResponseMessage assignRoute(Long busId, Long routeId, String username);

    /**
     * Otobüsün rotasındaki durakları getir
     */
    DataResponseMessage<List<StationDTO>> getRouteStations(Long busId, String username);

    /**
     * Belirli bir durağa tahmini varış süresini hesapla
     */
    DataResponseMessage<Double> getEstimatedArrivalTime(Long busId, Long stationId, String username);

    // === YÖN YÖNETİMİ ===

    /**
     * Otobüsün yönünü değiştir (gidiş ↔ dönüş)
     */
    ResponseMessage switchDirection(Long busId, String username) throws BusNotFoundException;

    // === İSTATİSTİKLER ===

    /**
     * Otobüs istatistiklerini getir
     */
    DataResponseMessage<Object> getBusStatistics(String username);

    // === FİLTRELEME VE ARAMA ===

    /**
     * Plakaya göre otobüs ara
     */
    DataResponseMessage<PageDTO<BusDTO>> searchByNumberPlate(String numberPlate, String username, int page, int size);

    /**
     * Rotaya göre otobüsleri getir
     */
    DataResponseMessage<PageDTO<BusDTO>> getBusesByRoute(Long routeId, String username, int page, int size);

    /**
     * Şoföre göre otobüsleri getir
     */
    DataResponseMessage<PageDTO<BusDTO>> getBusesByDriver(Long driverId, String username, int page, int size);

    // === DURUM YÖNETİMİ ===

    /**
     * Otobüs durumunu güncelle
     */
    ResponseMessage updateBusStatus(Long busId, BusStatusUpdateRequest request, String username)
            throws BusNotFoundException;

    /**
     * Yolcu sayısını güncelle
     */
    ResponseMessage updatePassengerCount(Long busId, Integer count, String username)
            throws BusNotFoundException;

    // === TOPLU İŞLEMLER ===

    /**
     * Toplu otobüs aktivasyonu
     */
    ResponseMessage bulkActivate(List<Long> busIds, String username);

    /**
     * Toplu otobüs deaktivasyonu
     */
    ResponseMessage bulkDeactivate(List<Long> busIds, String username);
}