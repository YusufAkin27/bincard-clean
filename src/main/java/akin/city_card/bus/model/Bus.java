package akin.city_card.bus.model;

import akin.city_card.buscard.model.CardType;
import akin.city_card.driver.model.Driver;
import akin.city_card.route.model.Route;
import akin.city_card.route.model.RouteDirection;
import akin.city_card.security.entity.SecurityUser;
import akin.city_card.station.model.Station;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Otobüs modeli - Temizlenmiş ve tutarlı hale getirildi
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "buses")
public class Bus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Plaka numarası
     */
    @Column(nullable = false, unique = true, length = 20)
    private String numberPlate;

    /**
     * Bu otobüsün atandığı ana rota
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_route_id")
    private Route assignedRoute;

    /**
     * Şu anda hangi yönde gidiyor?
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_direction_id")
    private RouteDirection currentDirection;

    /**
     * Otobüs şoförü
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    /**
     * Otobüs aktif mi?
     */
    @Column(nullable = false)
    private boolean isActive = true;

    /**
     * Temel bilet ücreti
     */
    @Column(nullable = false)
    private double baseFare;

    /**
     * Otobüs durumu
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BusStatus status = BusStatus.CALISIYOR;

    /**
     * Kapasite
     */
    @Column(nullable = false)
    private int capacity = 50; // Varsayılan kapasite

    /**
     * Şu anki yolcu sayısı
     */
    @Column(nullable = false)
    private int currentPassengerCount = 0;

    // Konum bilgileri
    private Double currentLatitude;
    private Double currentLongitude;
    private LocalDateTime lastLocationUpdate;
    private Double lastKnownSpeed;

    /**
     * En son görüldüğü durak
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_seen_station_id")
    private Station lastSeenStation;

    private LocalDateTime lastSeenStationTime;

    /**
     * Bir sonraki hedef durak
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_station_id")
    private Station nextStation;

    /**
     * Tahmini varış süresi (dakika)
     */
    private Integer estimatedArrivalMinutes;

    // Audit fields
    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private SecurityUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private SecurityUser updatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by")
    private SecurityUser deletedBy;

    // İlişkiler
    @OneToMany(mappedBy = "bus", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BusRide> rides;

    @OneToMany(mappedBy = "bus", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<BusLocation> locationHistory;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Kart tipine göre ücret hesapla
     */
    public double calculateFare(CardType cardType) {
        return switch (cardType) {
            case ÖĞRENCİ -> baseFare * 0.5;
            case ÖĞRETMEN -> baseFare * 0.75;
            case YAŞLI -> baseFare * 0.6;
            case ENGELLİ -> baseFare * 0.4;
            case ÇOCUK -> baseFare * 0.3;
            case TURİST -> baseFare * 1.2;
            case ABONMAN -> 0.0;
            case TAM -> baseFare;
        };
    }

    /**
     * Otobüs dolu mu?
     */
    public boolean isFull() {
        return currentPassengerCount >= capacity;
    }

    /**
     * Doluluk oranı (yüzde)
     */
    public double getOccupancyRate() {
        return capacity > 0 ? (double) currentPassengerCount / capacity * 100 : 0;
    }

    /**
     * Yön değiştir (gidiş ↔ dönüş)
     */
    public void switchDirection() {
        if (assignedRoute != null && currentDirection != null) {
            if (currentDirection.equals(assignedRoute.getOutgoingDirection())) {
                currentDirection = assignedRoute.getReturnDirection();
            } else {
                currentDirection = assignedRoute.getOutgoingDirection();
            }
        }
    }

    /**
     * Rota adı kısa gösterimi
     */
    public String getRouteDisplayName() {
        return assignedRoute != null ? assignedRoute.getName() : "Atanmamış";
    }

    /**
     * Rota kodu gösterimi
     */
    public String getRouteCode() {
        return assignedRoute != null ? assignedRoute.getCode() : null;
    }

    /**
     * Şoför adı gösterimi
     */
    public String getDriverDisplayName() {
        if (driver != null && driver.getProfileInfo() != null) {
            return driver.getProfileInfo().getName() + " " + driver.getProfileInfo().getSurname();
        }
        return "Atanmamış";
    }

    /**
     * Yön adı gösterimi
     */
    public String getCurrentDirectionName() {
        return currentDirection != null ? currentDirection.getName() : "Belirsiz";
    }
}