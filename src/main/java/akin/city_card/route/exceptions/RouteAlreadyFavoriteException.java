package akin.city_card.route.exceptions;

import akin.city_card.bus.model.Bus;
import akin.city_card.security.exception.BusinessException;

public class RouteAlreadyFavoriteException extends BusinessException {
    public RouteAlreadyFavoriteException( ) {
        super("Rota zaten favorilerde ekli");
    }
}
