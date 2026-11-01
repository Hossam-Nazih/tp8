package com.example.service;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.StatutReservation;
import com.example.model.Utilisateur;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Création, modification, annulation et suppression des réservations.
 * Chaque méthode ouvre son propre EntityManager et sa propre transaction (voir enTransaction).
 */
public class ReservationService {

    private static final Logger LOGGER = Logger.getLogger(ReservationService.class.getName());

    // Chevauchement : la réservation existante commence avant la fin du nouveau créneau et finit après son début.
    // 9h-11h puis 11h-12h ne se chevauchent pas. Une réservation annulée ne bloque plus rien.
    private static final String JPQL_CONFLITS =
            "SELECT COUNT(r) FROM Reservation r"
            + " WHERE r.salleConcernee.id = :salleId"
            + " AND r.statut = :confirmee"
            + " AND r.debutCreneau < :fin AND r.finCreneau > :debut"
            + " AND r.id <> :idExclu";

    private final EntityManagerFactory emf;

    public ReservationService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public Reservation creerReservation(Long salleId, Long utilisateurId, LocalDateTime debut, LocalDateTime fin,
                                        int participants, String motif) {
        validerCreneau(debut, fin);
        Reservation creee = enTransaction("création de réservation", em -> {
            // Verrou en écriture sur la ligne de la salle jusqu'au commit : deux demandes simultanées
            // pour la même salle passent l'une après l'autre, la seconde voit donc la réservation de la première.
            // (Ce find ne passe pas par le cache : un verrou se prend forcément en base.)
            Salle salle = em.find(Salle.class, salleId, LockModeType.PESSIMISTIC_WRITE);
            if (salle == null) {
                throw new ReservationException("Salle introuvable : " + salleId);
            }
            if (participants > salle.getCapaciteMax()) {
                throw new ReservationException("La salle " + salle.getCodeSalle() + " ne compte que "
                        + salle.getCapaciteMax() + " places (" + participants + " demandées)");
            }
            Utilisateur demandeur = em.find(Utilisateur.class, utilisateurId);
            if (demandeur == null) {
                throw new ReservationException("Utilisateur introuvable : " + utilisateurId);
            }
            verifierDisponibilite(em, salleId, debut, fin, null);

            Reservation reservation = new Reservation(salle, demandeur, debut, fin, participants, motif);
            em.persist(reservation);
            return reservation;
        });
        LOGGER.info("Création de la réservation réussie pour utilisateur : "
                + creee.getDemandeur().getAdresseMail() + " -> " + creee);
        return creee;
    }

    /**
     * Change le créneau d'une réservation lue plus tôt (par exemple affichée dans un formulaire).
     * Si quelqu'un l'a modifiée entre-temps, sa version en base est plus récente que celle de
     * {@code reservationLue} : merge() le détecte et la modification est refusée.
     */
    public Reservation modifierCreneau(Reservation reservationLue, LocalDateTime debut, LocalDateTime fin) {
        validerCreneau(debut, fin);
        Reservation modifiee = enTransaction("modification de créneau", em -> {
            Reservation geree = em.merge(reservationLue);
            verifierDisponibilite(em, geree.getSalleConcernee().getId(), debut, fin, geree.getId());
            geree.setDebutCreneau(debut);
            geree.setFinCreneau(fin);
            return geree;
        });
        LOGGER.info("Créneau modifié : " + modifiee);
        return modifiee;
    }

    public Reservation annulerReservation(Long reservationId) {
        Reservation annulee = enTransaction("annulation", em -> {
            Reservation reservation = trouverOuEchouer(em, reservationId);
            reservation.setStatut(StatutReservation.ANNULEE);
            return reservation;
        });
        LOGGER.info("Réservation annulée, le créneau est libéré : " + annulee);
        return annulee;
    }

    public void supprimerReservation(Long reservationId) {
        enTransaction("suppression", em -> {
            em.remove(trouverOuEchouer(em, reservationId));
            return null;
        });
        LOGGER.info("Réservation supprimée : #" + reservationId);
    }

    /** Relit une réservation avec sa salle et son demandeur, utilisables après fermeture de l'EntityManager. */
    public Reservation trouver(Long reservationId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT r FROM Reservation r JOIN FETCH r.salleConcernee JOIN FETCH r.demandeur"
                            + " WHERE r.id = :id", Reservation.class)
                    .setParameter("id", reservationId)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new ReservationException("Réservation introuvable : " + reservationId);
        } finally {
            em.close();
        }
    }

    private static void validerCreneau(LocalDateTime debut, LocalDateTime fin) {
        if (debut == null || fin == null || !fin.isAfter(debut)) {
            throw new ReservationException("Créneau invalide : la fin doit être après le début");
        }
        if (debut.isBefore(LocalDateTime.now())) {
            throw new ReservationException("Impossible de réserver dans le passé : " + debut);
        }
    }

    private static void verifierDisponibilite(EntityManager em, Long salleId, LocalDateTime debut,
                                              LocalDateTime fin, Long reservationIgnoree) {
        long conflits = em.createQuery(JPQL_CONFLITS, Long.class)
                .setParameter("salleId", salleId)
                .setParameter("confirmee", StatutReservation.CONFIRMEE)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                // -1 n'est l'identifiant d'aucune réservation : à la création, aucune n'est exclue
                .setParameter("idExclu", reservationIgnoree == null ? -1L : reservationIgnoree)
                .getSingleResult();
        if (conflits > 0) {
            throw new ReservationException("Créneau déjà réservé pour cette salle entre " + debut + " et " + fin);
        }
    }

    private static Reservation trouverOuEchouer(EntityManager em, Long reservationId) {
        Reservation reservation = em.find(Reservation.class, reservationId);
        if (reservation == null) {
            throw new ReservationException("Réservation introuvable : " + reservationId);
        }
        return reservation;
    }

    /**
     * Étapes 10 et 11 réunies : commit si tout va bien, rollback sinon, et chaque échec est journalisé
     * une fois, ici, avec le niveau qui convient. Les appelants n'ont plus qu'à écrire leur logique métier.
     */
    private <T> T enTransaction(String operation, Function<EntityManager, T> travail) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            T resultat = travail.apply(em);
            // flush avant commit : un conflit de version sort ici en OptimisticLockException,
            // au lieu d'arriver enveloppé dans une RollbackException au moment du commit
            em.flush();
            tx.commit();
            return resultat;
        } catch (OptimisticLockException e) {
            annuler(tx);
            LOGGER.warning("Conflit de version pendant la " + operation + " : " + e.getMessage());
            throw new ConflitVersionException(
                    "Conflit détecté : la réservation a été modifiée entre-temps, veuillez la relire et réessayer.", e);
        } catch (ReservationException e) {
            annuler(tx);
            LOGGER.warning("Refus de la " + operation + " : " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            annuler(tx);
            LOGGER.log(Level.SEVERE, "Erreur technique pendant la " + operation, e);
            throw e;
        } finally {
            em.close();
        }
    }

    private static void annuler(EntityTransaction tx) {
        if (tx.isActive()) {
            tx.rollback();
        }
    }
}
