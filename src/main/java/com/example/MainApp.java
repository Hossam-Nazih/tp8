package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.ConflitVersionException;
import com.example.service.CriteresRecherche;
import com.example.service.RechercheService;
import com.example.service.ReservationException;
import com.example.service.ReservationService;
import com.example.util.*;

import javax.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.util.DataInitializer.dansJours;

/**
 * Application complète (étape 8) : migration du schéma, jeu de données, scénarios métier, requêtes, cache,
 * puis rapport de performance. Le SQL envoyé par Hibernate s'affiche au fil de l'exécution.
 */
public class MainApp {

    private static final Logger LOGGER = Logger.getLogger(MainApp.class.getName());

    public static void main(String[] args) {
        LogConfig.initialiser();

        // Le schéma doit exister avant JPA : Hibernate (hbm2ddl=validate) refuserait de démarrer sans lui
        try {
            new MigrationExecutor(ConfigurationBD.charger()).executerToutes();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Migration impossible, arrêt de l'application. MySQL est-il démarré ?", e);
            return;
        }

        EntityManagerFactory emf = JpaUtil.getEntityManagerFactory();
        try {
            new DataInitializer(emf).initData();

            ReservationService reservations = new ReservationService(emf);
            RechercheService recherche = new RechercheService(emf);
            PerformanceReport rapport = new PerformanceReport(emf);
            rapport.reinitialiser();

            Salle a101 = recherche.trouverSalleParCode("A-101");
            Salle b110 = recherche.trouverSalleParCode("B-110");
            Utilisateur nadia = recherche.trouverUtilisateurParMail("nadia.tazi@campus.ma");

            titre("1. Réservation valide");
            Reservation creee = reservations.creerReservation(a101.getId(), nadia.getId(),
                    dansJours(1, 11), dansJours(1, 12), 8, "Entretien de recrutement");
            System.out.println("Créée : " + creee);

            titre("2. Cas refusés par les règles métier");
            tenter("Créneau déjà pris (A-101 demain 10h-11h)", () -> reservations.creerReservation(
                    a101.getId(), nadia.getId(), dansJours(1, 10), dansJours(1, 11), 5, "Point rapide"));
            tenter("Trop de participants (30 dans B-110)", () -> reservations.creerReservation(
                    b110.getId(), nadia.getId(), dansJours(5, 9), dansJours(5, 10), 30, "Séminaire"));
            tenter("Fin avant le début", () -> reservations.creerReservation(
                    b110.getId(), nadia.getId(), dansJours(5, 11), dansJours(5, 10), 4, "Erreur de saisie"));

            titre("3. Verrouillage optimiste (@Version)");
            // Deux personnes ouvrent la même réservation, chacune reçoit sa copie avec la version actuelle
            Reservation copieNadia = reservations.trouver(creee.getId());
            Reservation copieGestionnaire = reservations.trouver(creee.getId());
            reservations.modifierCreneau(copieGestionnaire, dansJours(2, 14), dansJours(2, 15));
            try {
                reservations.modifierCreneau(copieNadia, dansJours(2, 16), dansJours(2, 17));
            } catch (ConflitVersionException e) {
                System.out.println("Refusé : " + e.getMessage());
                // Reprise : on relit la version à jour, puis on rejoue la modification dessus
                Reservation aJour = reservations.trouver(creee.getId());
                Reservation reprise = reservations.modifierCreneau(aJour, dansJours(2, 16), dansJours(2, 17));
                System.out.println("Après relecture, modification acceptée : " + reprise);
            }

            titre("4. Annulation : le créneau redevient libre");
            reservations.annulerReservation(creee.getId());
            Reservation remplacante = reservations.creerReservation(a101.getId(), nadia.getId(),
                    dansJours(2, 16), dansJours(2, 17), 6, "Créneau récupéré après annulation");
            System.out.println("Créée sur le créneau libéré : " + remplacante);

            titre("5. JPQL et Criteria API");
            LocalDateTime maintenant = LocalDateTime.now();
            afficher("JPQL     - réservations à venir en A-101", recherche.reservationsAVenirJpql("A-101", maintenant));
            afficher("Criteria - réservations à venir en A-101", recherche.reservationsAVenirCriteria("A-101", maintenant));
            afficher("Criteria - Bâtiment Al Khawarizmi, 8 participants ou plus",
                    recherche.rechercher(new CriteresRecherche().batiment("Bâtiment Al Khawarizmi").participantsMin(8)));
            System.out.println("Salles libres demain 9h-11h pour 10 personnes : "
                    + recherche.sallesDisponibles(dansJours(1, 9), dansJours(1, 11), 10));

            titre("6. Cache de second niveau");
            emf.getCache().evict(Salle.class);
            long avant = rapport.requetesSql();
            recherche.trouverSalle(a101.getId());
            long apresPremiere = rapport.requetesSql();
            recherche.trouverSalle(a101.getId());
            long apresSeconde = rapport.requetesSql();
            System.out.println("1re lecture de A-101 (cache vide)  : " + (apresPremiere - avant) + " requête(s) SQL");
            System.out.println("2e lecture, autre EntityManager    : " + (apresSeconde - apresPremiere) + " requête(s) SQL");

            rapport.printReport();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Arrêt sur une erreur inattendue", e);
        } finally {
            JpaUtil.fermer();
        }
    }

    // Un refus métier attendu : on l'affiche sans arrêter le programme
    private static void tenter(String cas, Runnable action) {
        try {
            action.run();
            System.out.println(cas + " : accepté (inattendu)");
        } catch (ReservationException e) {
            System.out.println(cas + " : refusé -> " + e.getMessage());
        }
    }

    private static void afficher(String libelle, List<Reservation> liste) {
        System.out.println(libelle + " : " + liste.size());
        for (Reservation r : liste) {
            System.out.println("   " + r.getSalleConcernee().getCodeSalle() + "  " + r.getDebutCreneau()
                    + "  " + r.getMotif() + "  (" + r.getDemandeur().getNomPrenom() + ")");
        }
    }

    private static void titre(String texte) {
        System.out.println();
        System.out.println("===== " + texte + " =====");
    }
}
