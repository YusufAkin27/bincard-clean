package akin.city_card.route.core.request;

import akin.city_card.route.model.TimeSlot;
import lombok.Data;

import java.util.List;

@Data
public class CreateRouteNodeRequest {
    private Long fromStationId;
    private Long toStationId;

    private List<TimeSlot> weekdayHours;
    private List<TimeSlot> weekendHours;
}
