package com.example;

import com.example.model.Reservation;
import com.example.model.StatutReservation;
import com.example.model.Utilisateur;
import com.example.service.ConflitVersionException;
import com.example.service.ReservationException;
import com.example.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

import static com.example.util.DataInitializer.dansJours;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cas normaux et cas d'erreur de la gestion des réservations.
 * Jeu de données : A-101 (20 places) est réservée demain de 9h à 11h et de 14h à 16h.
 */
class ReservationServiceTest extends BaseDeTest {

    private ReservationService service;
    private Long a101;
    private Long nadia;

    @BeforeEach
    void preparer() {
        service = new ReservationService(emf);
        a101 = salle("A-101").getId();
        nadia = utilisateur("nadia.tazi@campus.ma").getId();
    }

    @Test
    void creerReservationSurUnCreneauLibre() {
        Reservation reservation = service.creerReservation(a101, nadia, dansJours(1, 11), dansJours(1, 12), 8, "Entretien");

        assertNotNull(reservation.getId());
        assertEquals(StatutReservation.CONFIRMEE, reservation.getStatut());
        assertNotNull(reservation.getDateCreation(), "renseignée par @PrePersist");
        assertEquals(0, reservation.getVersionLigne());
    }

    @Test
    void refuserUnCreneauQuiChevaucheUneReservation() {
        ReservationException refus = assertThrows(ReservationException.class, () ->
                service.creerReservation(a101, nadia, dansJours(1, 10), dansJours(1, 12), 5, "Conflit"));

        assertTrue(refus.getMessage().contains("déjà réservé"));
    }

    @Test
    void accepterDeuxCreneauxQuiSeTouchentSansSeChevaucher() {
        // 9h-11h existe déjà : 11h-14h commence pile à la fin et finit pile au début de 14h-16h
        assertNotNull(service.creerReservation(a101, nadia, dansJours(1, 11), dansJours(1, 14), 5, "Entre deux").getId());
    }

    @Test
    void refuserUneFinAvantLeDebut() {
        assertThrows(ReservationException.class, () ->
                service.creerReservation(a101, nadia, dansJours(5, 11), dansJours(5, 10), 5, "Inversé"));
    }

    @Test
    void refuserUnCreneauDansLePasse() {
        assertThrows(ReservationException.class, () ->
                service.creerReservation(a101, nadia, dansJours(-1, 9), dansJours(-1, 10), 5, "Hier"));
    }

    @Test
    void refuserPlusDeParticipantsQueDePlaces() {
        ReservationException refus = assertThrows(ReservationException.class, () ->
                service.creerReservation(a101, nadia, dansJours(5, 9), dansJours(5, 10), 21, "Trop de monde"));

        assertTrue(refus.getMessage().contains("20 places"));
    }

    @Test
    void refuserUneSalleInconnue() {
        assertThrows(ReservationException.class, () ->
                service.creerReservation(999_999L, nadia, dansJours(5, 9), dansJours(5, 10), 2, "Fantôme"));
    }

    @Test
    void uneReservationAnnuleeLibereSonCreneau() {
        Reservation existante = recherche.reservationsAVenirJpql("A-101", dansJours(0, 0)).get(0);

        service.annulerReservation(existante.getId());

        assertNotNull(service.creerReservation(a101, nadia, existante.getDebutCreneau(), existante.getFinCreneau(),
                5, "Reprise du créneau").getId());
    }

    @Test
    void uneModificationIncrementeLaVersion() {
        Reservation lue = service.trouver(creerUneReservation().getId());

        Reservation modifiee = service.modifierCreneau(lue, dansJours(6, 10), dansJours(6, 11));

        assertEquals(lue.getVersionLigne() + 1, modifiee.getVersionLigne());
        assertEquals(dansJours(6, 10), service.trouver(lue.getId()).getDebutCreneau());
    }

    @Test
    void modifierAvecUneVersionPerimeeEstRefuse() {
        Long id = creerUneReservation().getId();
        Reservation copieA = service.trouver(id);
        Reservation copieB = service.trouver(id);

        service.modifierCreneau(copieA, dansJours(6, 10), dansJours(6, 11));

        assertThrows(ConflitVersionException.class, () ->
                service.modifierCreneau(copieB, dansJours(6, 14), dansJours(6, 15)));
        assertEquals(dansJours(6, 10), service.trouver(id).getDebutCreneau(), "la première modification est conservée");
    }

    @Test
    void apresUnConflitRelireLaReservationPermetDeReussir() {
        Long id = creerUneReservation().getId();
        Reservation perimee = service.trouver(id);
        service.modifierCreneau(service.trouver(id), dansJours(6, 10), dansJours(6, 11));
        assertThrows(ConflitVersionException.class, () -> service.modifierCreneau(perimee, dansJours(6, 14), dansJours(6, 15)));

        Reservation reprise = service.modifierCreneau(service.trouver(id), dansJours(6, 14), dansJours(6, 15));

        assertEquals(dansJours(6, 14), reprise.getDebutCreneau());
        assertEquals(2, reprise.getVersionLigne());
    }

    @Test
    void supprimerUneReservation() {
        Long id = creerUneReservation().getId();

        service.supprimerReservation(id);

        assertThrows(ReservationException.class, () -> service.trouver(id));
    }

    @Test
    void lIndexUniqueDeLaMigration2InterditDeuxComptesAvecLeMemeMail() {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            // Avec IDENTITY, persist() envoie l'INSERT tout de suite pour obtenir l'id : c'est lui qui échoue
            assertThrows(PersistenceException.class, () ->
                    em.persist(new Utilisateur("Homonyme", "nadia.tazi@campus.ma", null, null)));
        } finally {
            em.getTransaction().rollback();
            em.close();
        }
    }

    private Reservation creerUneReservation() {
        return service.creerReservation(a101, nadia, dansJours(5, 9), dansJours(5, 10), 4, "Réservation de test");
    }
}
