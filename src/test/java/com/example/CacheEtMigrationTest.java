package com.example;

import com.example.model.Equipement;
import com.example.model.Salle;
import com.example.util.ConfigurationBD;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.*;

class CacheEtMigrationTest extends BaseDeTest {

    private PerformanceReport rapport;

    @BeforeEach
    void preparer() {
        rapport = new PerformanceReport(emf);
    }

    @Test
    void uneSalleDejaLueEstServieParLeCacheSansSql() {
        Long id = salle("A-101").getId();
        emf.getCache().evict(Salle.class);

        rapport.reinitialiser();
        recherche.trouverSalle(id);
        assertEquals(1, rapport.requetesSql(), "cache vide : lecture en base");

        rapport.reinitialiser();
        Salle salle = recherche.trouverSalle(id);
        assertEquals(0, rapport.requetesSql(), "autre EntityManager, même salle : servie par le cache");
        assertEquals("A-101", salle.getCodeSalle());
    }

    @Test
    void laRequeteCacheableParCodeNeRetourneEnBaseQuUneFois() {
        recherche.trouverSalleParCode("B-110");

        rapport.reinitialiser();
        recherche.trouverSalleParCode("B-110");

        assertEquals(0, rapport.requetesSql());
    }

    @Test
    void lesEquipementsSontEnCacheEnLectureSeule() {
        Long id = premierEquipement();
        lireEquipement(id);

        rapport.reinitialiser();
        Equipement equipement = lireEquipement(id);

        assertEquals(0, rapport.requetesSql());
        assertTrue(emf.getCache().contains(Equipement.class, id));
        assertNotNull(equipement.getLibelle());
    }

    @Test
    void lesMigrationsDejaAppliqueesNeSontPasRejouees() throws Exception {
        assertEquals(0, new MigrationExecutor(ConfigurationBD.charger()).executerToutes());
    }

    @Test
    void laColonneDescriptionDeLaMigration2EstUtilisee() {
        assertEquals("Salle de visioconférence", salle("B-110").getDescription());
    }

    private Long premierEquipement() {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT MIN(e.id) FROM Equipement e", Long.class).getSingleResult();
        } finally {
            em.close();
        }
    }

    private Equipement lireEquipement(Long id) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.find(Equipement.class, id);
        } finally {
            em.close();
        }
    }
}
