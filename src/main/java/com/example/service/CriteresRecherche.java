package com.example.service;

import com.example.model.StatutReservation;

import java.time.LocalDateTime;

/**
 * Filtres facultatifs de la recherche de réservations : seuls ceux renseignés deviennent des conditions WHERE.
 * Exemple : new CriteresRecherche().batiment("Pavillon Averroès").participantsMin(10)
 */
public class CriteresRecherche {

    private String codeSalle;
    private String nomBatiment;
    private String adresseMailDemandeur;
    private LocalDateTime debutApres;
    private LocalDateTime debutAvant;
    private Integer participantsMin;
    private StatutReservation statut;

    public CriteresRecherche salle(String codeSalle) { this.codeSalle = codeSalle; return this; }
    public CriteresRecherche batiment(String nomBatiment) { this.nomBatiment = nomBatiment; return this; }
    public CriteresRecherche demandeur(String adresseMail) { this.adresseMailDemandeur = adresseMail; return this; }
    public CriteresRecherche debutApres(LocalDateTime date) { this.debutApres = date; return this; }
    public CriteresRecherche debutAvant(LocalDateTime date) { this.debutAvant = date; return this; }
    public CriteresRecherche participantsMin(int nombre) { this.participantsMin = nombre; return this; }
    public CriteresRecherche statut(StatutReservation statut) { this.statut = statut; return this; }

    public String getCodeSalle() { return codeSalle; }
    public String getNomBatiment() { return nomBatiment; }
    public String getAdresseMailDemandeur() { return adresseMailDemandeur; }
    public LocalDateTime getDebutApres() { return debutApres; }
    public LocalDateTime getDebutAvant() { return debutAvant; }
    public Integer getParticipantsMin() { return participantsMin; }
    public StatutReservation getStatut() { return statut; }
}
