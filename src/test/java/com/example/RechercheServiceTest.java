package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.StatutReservation;
import com.example.service.CriteresRecherche;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.util.DataInitializer.dansJours;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

class RechercheServiceTest extends BaseDeTest {

    @Test
    void jpqlEtCriteriaRenvoientLesMemesReservations() {
        LocalDateTime maintenant = LocalDateTime.now();

        List<Long> jpql = ids(recherche.reservationsAVenirJpql("A-101", maintenant));
        List<Long> criteria = ids(recherche.reservationsAVenirCriteria("A-101", maintenant));

        assertEquals(3, jpql.size());
        assertEquals(jpql, criteria);
    }

    @Test
    void lesAssociationsSontChargeesEtUtilisablesApresFermeture() {
        Reservation premiere = recherche.reservationsAVenirJpql("A-101", LocalDateTime.now()).get(0);

        // Sans JOIN FETCH / fetch(), ces appels lèveraient une LazyInitializationException
        assertEquals("A-101", premiere.getSalleConcernee().getCodeSalle());
        assertEquals("Salma Bennani", premiere.getDemandeur().getNomPrenom());
    }

    @Test
    void rechercheParBatimentEtNombreDeParticipants() {
        List<Reservation> resultat = recherche.rechercher(
                new CriteresRecherche().batiment("Bâtiment Al Khawarizmi").participantsMin(8));

        assertEquals(asList("Conférence de rentrée", "Point projet"),
                resultat.stream().map(Reservation::getMotif).collect(Collectors.toList()));
    }

    @Test
    void rechercheParDemandeurEtStatut() {
        List<Reservation> annulees = recherche.rechercher(
                new CriteresRecherche().demandeur("karim.idrissi@campus.ma").statut(StatutReservation.ANNULEE));

        assertEquals(1, annulees.size());
        assertEquals("C-010", annulees.get(0).getSalleConcernee().getCodeSalle());
    }

    @Test
    void sansCritereToutesLesReservationsSontRenvoyees() {
        assertEquals(8, recherche.rechercher(new CriteresRecherche()).size());
    }

    @Test
    void sallesDisponiblesExclutLesSallesOccupeesEtTropPetites() {
        List<String> libres = recherche.sallesDisponibles(dansJours(1, 9), dansJours(1, 11), 10).stream()
                .map(Salle::getCodeSalle).collect(Collectors.toList());

        // A-101, B-001 et B-110 sont occupées ; C-010 (8 places) est trop petite
        assertEquals(asList("B-115", "A-204"), libres);
    }

    @Test
    void uneReservationAnnuleeNeRendPasLaSalleIndisponible() {
        // C-010 n'a qu'une réservation demain 9h-10h, annulée
        List<String> libres = recherche.sallesDisponibles(dansJours(1, 9), dansJours(1, 10), 2).stream()
                .map(Salle::getCodeSalle).collect(Collectors.toList());

        assertTrue(libres.contains("C-010"));
    }

    private static List<Long> ids(List<Reservation> reservations) {
        return reservations.stream().map(Reservation::getId).collect(Collectors.toList());
    }
}
