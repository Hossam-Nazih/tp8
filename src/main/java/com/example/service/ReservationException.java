package com.example.service;

/**
 * Refus métier (créneau déjà pris, salle trop petite...). Le message est destiné à l'utilisateur,
 * contrairement aux erreurs techniques (base injoignable...) qui restent des exceptions JPA.
 */
public class ReservationException extends RuntimeException {

    public ReservationException(String message) {
        super(message);
    }

    public ReservationException(String message, Throwable cause) {
        super(message, cause);
    }
}
