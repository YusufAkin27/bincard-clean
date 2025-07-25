package akin.city_card.route.core.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NextBusDTO {
    private String plate;
    private Integer arrivalInMinutes;
}