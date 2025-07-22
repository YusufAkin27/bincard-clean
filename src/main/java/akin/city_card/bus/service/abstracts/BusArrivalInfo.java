package akin.city_card.bus.service.abstracts;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BusArrivalInfo {
    private String plate;
    private String arrivalText;           // Örnek: "34 AB 1234 plakalı otobüs 10 dakika sonra A durağına varacaktır"
    private int estimatedArrivalMinutes;  // Tahmini geliş süresi (dakika)
    private boolean arrivingSoon;         // <= 2 dakika
    private boolean delayed;
    private String delayReason;
    private LocalDateTime calculatedAt;   // Hesaplanma zamanı

    public boolean isArrivingSoon() {
        return estimatedArrivalMinutes <= 2;
    }

    public String getFormattedArrivalTime() {
        if (estimatedArrivalMinutes <= 0) return "Yakında";
        else if (estimatedArrivalMinutes == 1) return "1 dakika";
        else return estimatedArrivalMinutes + " dakika";
    }
}
