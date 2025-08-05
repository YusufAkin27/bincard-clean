package akin.city_card.security.exception;

public class AccountFrozenException extends BusinessException {
    public AccountFrozenException(long remainingMinutes) {
        super("Hesap kilitli. " + remainingMinutes + " dakika sonra tekrar deneyin.");
    }
}
