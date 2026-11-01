package com.example.model;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;

/**
 * Équipement présent dans une salle (vidéoprojecteur, visioconférence...).
 * Liste fixe, livrée par la migration 3.0 et jamais modifiée par l'application.
 */
@Entity
@Table(name = "equipements")
@Cacheable
// @Immutable : Hibernate ne génère jamais d'UPDATE pour cette entité, ce qui autorise READ_ONLY,
// la stratégie de cache la plus rapide (aucun verrou, aucune invalidation)
@Immutable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class Equipement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String libelle;

    protected Equipement() {}

    public Long getId() { return id; }
    public String getLibelle() { return libelle; }

    @Override
    public String toString() {
        return libelle;
    }
}
