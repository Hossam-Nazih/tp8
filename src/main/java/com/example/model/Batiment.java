package com.example.model;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Bâtiment du campus. Côté "un" de la relation avec Salle : la clé étrangère est dans salles.batiment_id.
 */
@Entity
@Table(name = "batiments")
@Cacheable
// NONSTRICT_READ_WRITE : pas de verrou dans le cache. Si l'adresse d'un bâtiment change,
// un autre utilisateur peut voir l'ancienne pendant un court instant, ce qui est sans conséquence ici.
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class Batiment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_batiment", nullable = false, unique = true, length = 80)
    private String nomBatiment;

    @Column(length = 200)
    private String adresse;

    @Column(name = "nombre_etages", nullable = false)
    private int nombreEtages;

    @OneToMany(mappedBy = "batiment")
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private List<Salle> sallesDuBatiment = new ArrayList<>();

    public Batiment() {}

    public Batiment(String nomBatiment, String adresse, int nombreEtages) {
        this.nomBatiment = nomBatiment;
        this.adresse = adresse;
        this.nombreEtages = nombreEtages;
    }

    // Met à jour les deux côtés : sans cela la liste en mémoire ne contiendrait pas la nouvelle salle
    public void ajouterSalle(Salle salle) {
        sallesDuBatiment.add(salle);
        salle.setBatiment(this);
    }

    public Long getId() { return id; }
    public String getNomBatiment() { return nomBatiment; }
    public void setNomBatiment(String nomBatiment) { this.nomBatiment = nomBatiment; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }
    public int getNombreEtages() { return nombreEtages; }
    public void setNombreEtages(int nombreEtages) { this.nombreEtages = nombreEtages; }
    public List<Salle> getSallesDuBatiment() { return sallesDuBatiment; }

    @Override
    public String toString() {
        return nomBatiment;
    }
}
