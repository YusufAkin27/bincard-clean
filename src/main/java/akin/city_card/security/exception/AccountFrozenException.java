package akin.city_card.security.exception;

public class AccountFrozenException extends BusinessException {
    public AccountFrozenException( ) {
        super("Hesabınız dondurulmuş. Aktifleştirmek ister misiniz?");
    }
}
