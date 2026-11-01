package com.example.model;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Réservation d'une salle par un utilisateur sur un créneau.
 *
 * Volontairement absente du cache de second niveau : les réservations sont créées et modifiées sans arrêt,
 * le cache passerait son temps à être invalidé et n'apporterait rien.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Une réservation concerne une seule salle, une salle a plusieurs réservations : ManyToOne.
    // LAZY : la salle n'est chargée que si on y accède (et souvent depuis le cache de second niveau).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salle_id", nullable = false)
    private Salle salleConcernee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur demandeur;

    @Column(name = "debut_creneau", nullable = false)
    private LocalDateTime debutCreneau;

    @Column(name = "fin_creneau", nullable = false)
    private LocalDateTime finCreneau;

    @Column(name = "nombre_participants", nullable = false)
    private int nombreParticipants;

    @Column(nullable = false, length = 200)
    private String motif;

    // STRING plutôt que ORDINAL : ajouter une valeur à l'enum ne décale pas celles déjà en base
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutReservation statut = StatutReservation.CONFIRMEE;

    @Column(name = "date_creation", nullable = false, updatable = false)
    private LocalDateTime dateCreation;

    // Incrémenté à chaque UPDATE. Un UPDATE fait avec une version périmée ne touche aucune ligne
    // et Hibernate lève une OptimisticLockException (voir ReservationService.modifierCreneau)
    @Version
    @Column(name = "version_ligne", nullable = false)
    private int versionLigne;

    public Reservation() {}

    public Reservation(Salle salleConcernee, Utilisateur demandeur, LocalDateTime debutCreneau,
                       LocalDateTime finCreneau, int nombreParticipants, String motif) {
        this.salleConcernee = salleConcernee;
        this.demandeur = demandeur;
        this.debutCreneau = debutCreneau;
        this.finCreneau = finCreneau;
        this.nombreParticipants = nombreParticipants;
        this.motif = motif;
    }

    @PrePersist
    void avantPremierEnregistrement() {
        dateCreation = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Salle getSalleConcernee() { return salleConcernee; }
    public void setSalleConcernee(Salle salleConcernee) { this.salleConcernee = salleConcernee; }
    public Utilisateur getDemandeur() { return demandeur; }
    public void setDemandeur(Utilisateur demandeur) { this.demandeur = demandeur; }
    public LocalDateTime getDebutCreneau() { return debutCreneau; }
    public void setDebutCreneau(LocalDateTime debutCreneau) { this.debutCreneau = debutCreneau; }
    public LocalDateTime getFinCreneau() { return finCreneau; }
    public void setFinCreneau(LocalDateTime finCreneau) { this.finCreneau = finCreneau; }
    public int getNombreParticipants() { return nombreParticipants; }
    public void setNombreParticipants(int nombreParticipants) { this.nombreParticipants = nombreParticipants; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public StatutReservation getStatut() { return statut; }
    public void setStatut(StatutReservation statut) { this.statut = statut; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public int getVersionLigne() { return versionLigne; }

    @Override
    public String toString() {
        return "Réservation#" + id + " [" + motif + ", " + debutCreneau + " -> " + finCreneau
                + ", " + statut + ", v" + versionLigne + "]";
    }
}
