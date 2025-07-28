package akin.city_card.bus.exceptions;

import akin.city_card.security.exception.BusinessException;
import lombok.extern.java.Log;

public class BusAlreadyAssignedAnotherDriverException extends BusinessException {

    public BusAlreadyAssignedAnotherDriverException(String numberPlate) {
        super("Otobüs zaten başka bir şoföre atanmış. Plaka: " + numberPlate);
    }
}
