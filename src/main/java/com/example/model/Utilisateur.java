package com.example.model;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

/**
 * Personne qui réserve une salle.
 */
@Entity
@Table(name = "utilisateurs")
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_prenom", nullable = false, length = 100)
    private String nomPrenom;

    // L'index unique correspondant est créé par la migration 2.0
    @Column(name = "adresse_mail", nullable = false, unique = true, length = 120)
    private String adresseMail;

    @Column(length = 20)
    private String telephone;

    @Column(length = 60)
    private String departement;

    @Version
    @Column(name = "version_ligne", nullable = false)
    private int versionLigne;

    public Utilisateur() {}

    public Utilisateur(String nomPrenom, String adresseMail, String telephone, String departement) {
        this.nomPrenom = nomPrenom;
        this.adresseMail = adresseMail;
        this.telephone = telephone;
        this.departement = departement;
    }

    public Long getId() { return id; }
    public String getNomPrenom() { return nomPrenom; }
    public void setNomPrenom(String nomPrenom) { this.nomPrenom = nomPrenom; }
    public String getAdresseMail() { return adresseMail; }
    public void setAdresseMail(String adresseMail) { this.adresseMail = adresseMail; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getDepartement() { return departement; }
    public void setDepartement(String departement) { this.departement = departement; }
    public int getVersionLigne() { return versionLigne; }

    @Override
    public String toString() {
        return nomPrenom + " <" + adresseMail + ">";
    }
}
