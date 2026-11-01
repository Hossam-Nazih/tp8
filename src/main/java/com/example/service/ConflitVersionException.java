package com.example.service;

/**
 * La réservation a été modifiée par quelqu'un d'autre depuis qu'on l'a lue (OptimisticLockException).
 * L'appelant doit la relire puis réessayer : refaire la même modification à l'aveugle écraserait l'autre.
 */
public class ConflitVersionException extends ReservationException {

    public ConflitVersionException(String message, Throwable cause) {
        super(message, cause);
    }
}
