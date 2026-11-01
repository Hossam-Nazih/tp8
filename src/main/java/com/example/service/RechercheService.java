package com.example.service;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.StatutReservation;
import com.example.model.Utilisateur;
import org.hibernate.annotations.QueryHints;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Lectures (étape 9). Toutes les valeurs passent par des paramètres nommés, jamais par concaténation :
 * la base reçoit la valeur à part de la requête, une injection SQL est donc impossible.
 */
public class RechercheService {

    private final EntityManagerFactory emf;

    public RechercheService(EntityManagerFactory emf) {
        this.emf = emf;
    }

    /**
     * Version JPQL. JOIN FETCH ramène salle et demandeur dans la même requête :
     * sans lui, afficher la liste déclencherait une requête de plus par réservation (problème N+1).
     */
    public List<Reservation> reservationsAVenirJpql(String codeSalle, LocalDateTime apres) {
        return lire(em -> em.createQuery(
                        "SELECT r FROM Reservation r"
                        + " JOIN FETCH r.salleConcernee s JOIN FETCH r.demandeur"
                        + " WHERE s.codeSalle = :codeSalle AND r.debutCreneau > :apres AND r.statut = :confirmee"
                        + " ORDER BY r.debutCreneau", Reservation.class)
                .setParameter("codeSalle", codeSalle)
                .setParameter("apres", apres)
                .setParameter("confirmee", StatutReservation.CONFIRMEE)
                .getResultList());
    }

    /** Même requête que reservationsAVenirJpql, écrite avec la Criteria API. */
    public List<Reservation> reservationsAVenirCriteria(String codeSalle, LocalDateTime apres) {
        return rechercher(new CriteresRecherche()
                .salle(codeSalle)
                .debutApres(apres)
                .statut(StatutReservation.CONFIRMEE));
    }

    /**
     * Recherche multicritère : c'est là que Criteria est plus pratique que JPQL,
     * on ajoute une condition par filtre renseigné au lieu de concaténer des morceaux de chaîne.
     */
    @SuppressWarnings("unchecked")
    public List<Reservation> rechercher(CriteresRecherche criteres) {
        return lire(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Reservation> cq = cb.createQuery(Reservation.class);
            Root<Reservation> reservation = cq.from(Reservation.class);

            // fetch() charge les associations comme un JOIN FETCH ; le transtypage en Join permet
            // en plus de filtrer dessus (pratique courante avec Hibernate, sans seconde jointure)
            Join<Reservation, Salle> salle = (Join<Reservation, Salle>) reservation.<Reservation, Salle>fetch("salleConcernee");
            Join<Reservation, Utilisateur> demandeur =
                    (Join<Reservation, Utilisateur>) reservation.<Reservation, Utilisateur>fetch("demandeur");

            List<Predicate> conditions = new ArrayList<>();
            if (criteres.getCodeSalle() != null) {
                conditions.add(cb.equal(salle.get("codeSalle"), criteres.getCodeSalle()));
            }
            if (criteres.getNomBatiment() != null) {
                // Simple chemin salle.batiment.nomBatiment : Hibernate 5 ne sait pas ajouter un join()
                // derrière un fetch(), mais génère seul la jointure pour un chemin ManyToOne
                conditions.add(cb.equal(salle.get("batiment").get("nomBatiment"), criteres.getNomBatiment()));
            }
            if (criteres.getAdresseMailDemandeur() != null) {
                conditions.add(cb.equal(demandeur.get("adresseMail"), criteres.getAdresseMailDemandeur()));
            }
            if (criteres.getDebutApres() != null) {
                conditions.add(cb.greaterThan(reservation.<LocalDateTime>get("debutCreneau"), criteres.getDebutApres()));
            }
            if (criteres.getDebutAvant() != null) {
                conditions.add(cb.lessThan(reservation.<LocalDateTime>get("debutCreneau"), criteres.getDebutAvant()));
            }
            if (criteres.getParticipantsMin() != null) {
                conditions.add(cb.ge(reservation.<Integer>get("nombreParticipants"), criteres.getParticipantsMin()));
            }
            if (criteres.getStatut() != null) {
                conditions.add(cb.equal(reservation.get("statut"), criteres.getStatut()));
            }

            cq.select(reservation)
                    .where(conditions.toArray(new Predicate[0]))
                    .orderBy(cb.asc(reservation.get("debutCreneau")));
            return em.createQuery(cq).getResultList();
        });
    }

    /** Salles assez grandes et sans réservation confirmée qui chevauche le créneau demandé. */
    public List<Salle> sallesDisponibles(LocalDateTime debut, LocalDateTime fin, int participants) {
        return lire(em -> em.createQuery(
                        "SELECT s FROM Salle s"
                        + " WHERE s.capaciteMax >= :participants"
                        + " AND NOT EXISTS (SELECT r FROM Reservation r WHERE r.salleConcernee = s"
                        + "   AND r.statut = :confirmee AND r.debutCreneau < :fin AND r.finCreneau > :debut)"
                        + " ORDER BY s.capaciteMax", Salle.class)
                .setParameter("participants", participants)
                .setParameter("confirmee", StatutReservation.CONFIRMEE)
                .setParameter("debut", debut)
                .setParameter("fin", fin)
                .getResultList());
    }

    /**
     * Recherche par code, marquée cacheable : le cache de requêtes garde l'identifiant trouvé
     * et le cache de second niveau la salle elle-même. Le second appel n'envoie aucun SQL.
     */
    public Salle trouverSalleParCode(String codeSalle) {
        return lire(em -> {
            try {
                return em.createQuery("SELECT s FROM Salle s WHERE s.codeSalle = :code", Salle.class)
                        .setParameter("code", codeSalle)
                        .setHint(QueryHints.CACHEABLE, true)
                        .getSingleResult();
            } catch (NoResultException e) {
                throw new ReservationException("Salle introuvable : " + codeSalle);
            }
        });
    }

    /** em.find() regarde d'abord le cache de premier niveau, puis le second niveau, et seulement ensuite la base. */
    public Salle trouverSalle(Long salleId) {
        return lire(em -> em.find(Salle.class, salleId));
    }

    public Utilisateur trouverUtilisateurParMail(String adresseMail) {
        return lire(em -> em.createQuery("SELECT u FROM Utilisateur u WHERE u.adresseMail = :mail", Utilisateur.class)
                .setParameter("mail", adresseMail)
                .setHint(QueryHints.CACHEABLE, true)
                .getSingleResult());
    }

    // Lecture seule : pas de transaction nécessaire, mais l'EntityManager est toujours fermé
    private <T> T lire(Function<EntityManager, T> lecture) {
        EntityManager em = emf.createEntityManager();
        try {
            return lecture.apply(em);
        } finally {
            em.close();
        }
    }
}
