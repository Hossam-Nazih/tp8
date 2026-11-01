package com.example.model;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Salle réservable. Lue à chaque création de réservation : c'est l'entité qui profite le plus du cache.
 */
@Entity
@Table(name = "salles")
@Cacheable
// READ_WRITE : une salle peut être modifiée (capacité, description). Pendant la modification,
// l'entrée du cache est verrouillée et les autres lecteurs vont en base : jamais de valeur périmée.
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Salle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identifiant lisible affiché sur la porte, par exemple "B-204"
    @Column(name = "code_salle", nullable = false, unique = true, length = 20)
    private String codeSalle;

    @Column(name = "capacite_max", nullable = false)
    private int capaciteMax;

    @Column(name = "numero_etage", nullable = false)
    private int numeroEtage;

    // Colonne ajoutée par la migration 2.0
    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batiment_id", nullable = false)
    private Batiment batiment;

    // Set et non List : Hibernate gère mal plusieurs collections List chargées dans une même requête
    @ManyToMany
    @JoinTable(name = "salle_equipements",
            joinColumns = @JoinColumn(name = "salle_id"),
            inverseJoinColumns = @JoinColumn(name = "equipement_id"))
    @Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private Set<Equipement> equipements = new HashSet<>();

    // Deux gestionnaires qui modifient la même salle en même temps : le second reçoit une OptimisticLockException
    @Version
    @Column(name = "version_ligne", nullable = false)
    private int versionLigne;

    public Salle() {}

    public Salle(String codeSalle, int capaciteMax, int numeroEtage, String description) {
        this.codeSalle = codeSalle;
        this.capaciteMax = capaciteMax;
        this.numeroEtage = numeroEtage;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getCodeSalle() { return codeSalle; }
    public void setCodeSalle(String codeSalle) { this.codeSalle = codeSalle; }
    public int getCapaciteMax() { return capaciteMax; }
    public void setCapaciteMax(int capaciteMax) { this.capaciteMax = capaciteMax; }
    public int getNumeroEtage() { return numeroEtage; }
    public void setNumeroEtage(int numeroEtage) { this.numeroEtage = numeroEtage; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Batiment getBatiment() { return batiment; }
    public void setBatiment(Batiment batiment) { this.batiment = batiment; }
    public Set<Equipement> getEquipements() { return equipements; }
    public int getVersionLigne() { return versionLigne; }

    @Override
    public String toString() {
        return codeSalle + " (" + capaciteMax + " places, étage " + numeroEtage + ")";
    }
}
