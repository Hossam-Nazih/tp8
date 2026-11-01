package com.example.util;

import com.example.model.*;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Remet la base dans un état connu : 3 bâtiments, 6 salles équipées, 5 utilisateurs, 8 réservations.
 * Les dates sont calculées à partir d'aujourd'hui, les réservations sont donc toujours à venir.
 */
public class DataInitializer {

    private static final Logger LOGGER = Logger.getLogger(DataInitializer.class.getName());

    private final EntityManagerFactory emf;

    public DataInitializer(EntityManagerFactory emf) {
        this.emf = emf;
    }

    /** Créneau qui commence dans {@code jours} jour(s) à {@code heure} heures pile. */
    public static LocalDateTime dansJours(int jours, int heure) {
        return LocalDate.now().plusDays(jours).atTime(heure, 0);
    }

    public void initData() {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            vider(em);

            Map<String, Equipement> equipements = equipementsParLibelle(em);

            Batiment ibnSina = new Batiment("Bâtiment Ibn Sina", "12 avenue des Facultés", 3);
            Batiment khawarizmi = new Batiment("Bâtiment Al Khawarizmi", "4 rue de l'Innovation", 2);
            Batiment averroes = new Batiment("Pavillon Averroès", "Campus nord", 1);
            em.persist(ibnSina);
            em.persist(khawarizmi);
            em.persist(averroes);

            Salle a101 = salle(em, ibnSina, "A-101", 20, 1, "Salle de réunion lumineuse",
                    equipements, "Vidéoprojecteur", "Tableau blanc");
            Salle a204 = salle(em, ibnSina, "A-204", 40, 2, "Salle de cours avec estrade",
                    equipements, "Vidéoprojecteur", "Climatisation");
            Salle b001 = salle(em, khawarizmi, "B-001", 120, 0, "Amphithéâtre principal",
                    equipements, "Vidéoprojecteur", "Visioconférence", "Climatisation");
            Salle b110 = salle(em, khawarizmi, "B-110", 12, 1, "Salle de visioconférence",
                    equipements, "Visioconférence", "Tableau blanc");
            salle(em, khawarizmi, "B-115", 25, 1, "Salle informatique",
                    equipements, "Postes informatiques", "Vidéoprojecteur");
            Salle c010 = salle(em, averroes, "C-010", 8, 0, "Petit bureau d'entretien",
                    equipements, "Tableau blanc");

            Utilisateur salma = utilisateur(em, "Salma Bennani", "salma.bennani@campus.ma", "0612345678", "Informatique");
            Utilisateur youssef = utilisateur(em, "Youssef El Amrani", "youssef.elamrani@campus.ma", "0623456789", "Mathématiques");
            Utilisateur claire = utilisateur(em, "Claire Martin", "claire.martin@campus.ma", "0634567890", "Langues");
            Utilisateur karim = utilisateur(em, "Karim Idrissi", "karim.idrissi@campus.ma", null, "Direction");
            utilisateur(em, "Nadia Tazi", "nadia.tazi@campus.ma", "0656789012", "Ressources humaines");

            reservation(em, a101, salma, dansJours(1, 9), dansJours(1, 11), 15, "Réunion d'équipe");
            reservation(em, a101, youssef, dansJours(1, 14), dansJours(1, 16), 10, "Soutenance de stage");
            reservation(em, a101, claire, dansJours(3, 10), dansJours(3, 12), 18, "Atelier d'écriture");
            reservation(em, a204, youssef, dansJours(2, 8), dansJours(2, 12), 35, "Cours d'analyse");
            reservation(em, b001, karim, dansJours(1, 10), dansJours(1, 12), 110, "Conférence de rentrée");
            reservation(em, b110, claire, dansJours(1, 9), dansJours(1, 10), 6, "Entretien à distance");
            reservation(em, b110, salma, dansJours(4, 15), dansJours(4, 17), 8, "Point projet");
            Reservation annulee = reservation(em, c010, karim, dansJours(1, 9), dansJours(1, 10), 3, "Entretien annuel");
            annulee.setStatut(StatutReservation.ANNULEE);

            tx.commit();
            LOGGER.info("Jeu de données inséré : 3 bâtiments, 6 salles, 5 utilisateurs, 8 réservations");
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            LOGGER.severe("Insertion du jeu de données impossible : " + e.getMessage());
            throw e;
        } finally {
            em.close();
        }
        // Les suppressions SQL directes ne passent pas par le cache : on le vide pour ne pas resservir d'anciennes salles
        emf.getCache().evictAll();
    }

    // Ordre imposé par les clés étrangères : les tables qui référencent d'abord.
    // Les équipements, données de référence de la migration 3.0, sont conservés.
    private static void vider(EntityManager em) {
        String[] tables = {"reservations", "salle_equipements", "salles", "batiments", "utilisateurs"};
        for (String table : tables) {
            em.createNativeQuery("DELETE FROM " + table).executeUpdate();
        }
    }

    private static Map<String, Equipement> equipementsParLibelle(EntityManager em) {
        List<Equipement> liste = em.createQuery("SELECT e FROM Equipement e", Equipement.class).getResultList();
        if (liste.isEmpty()) {
            throw new IllegalStateException("Aucun équipement en base : la migration v3_0 n'a pas été appliquée");
        }
        Map<String, Equipement> parLibelle = new HashMap<>();
        for (Equipement equipement : liste) {
            parLibelle.put(equipement.getLibelle(), equipement);
        }
        return parLibelle;
    }

    private static Salle salle(EntityManager em, Batiment batiment, String code, int capacite, int etage,
                               String description, Map<String, Equipement> equipements, String... libelles) {
        Salle salle = new Salle(code, capacite, etage, description);
        batiment.ajouterSalle(salle);
        for (String libelle : libelles) {
            salle.getEquipements().add(equipements.get(libelle));
        }
        em.persist(salle);
        return salle;
    }

    private static Utilisateur utilisateur(EntityManager em, String nomPrenom, String adresseMail,
                                           String telephone, String departement) {
        Utilisateur utilisateur = new Utilisateur(nomPrenom, adresseMail, telephone, departement);
        em.persist(utilisateur);
        return utilisateur;
    }

    private static Reservation reservation(EntityManager em, Salle salle, Utilisateur demandeur, LocalDateTime debut,
                                           LocalDateTime fin, int participants, String motif) {
        Reservation reservation = new Reservation(salle, demandeur, debut, fin, participants, motif);
        em.persist(reservation);
        return reservation;
    }
}
