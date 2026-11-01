package com.example;

import com.example.model.Salle;
import com.example.model.Utilisateur;
import com.example.service.RechercheService;
import com.example.util.ConfigurationBD;
import com.example.util.DataInitializer;
import com.example.util.JpaUtil;
import com.example.util.LogConfig;
import com.example.util.MigrationExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import javax.persistence.EntityManagerFactory;

/**
 * Préparation commune : les tests tournent sur H2 en mode MySQL (propriétés du pom.xml),
 * avec le schéma créé par les vrais scripts de migration. Chaque test repart du même jeu de données.
 */
abstract class BaseDeTest {

    protected static EntityManagerFactory emf;
    protected RechercheService recherche;

    @BeforeAll
    static void demarrer() throws Exception {
        LogConfig.initialiser();
        // La base H2 vit tant que la JVM tourne : seule la première classe de test applique réellement les scripts
        new MigrationExecutor(ConfigurationBD.charger()).executerToutes();
        emf = JpaUtil.getEntityManagerFactory();
    }

    @BeforeEach
    void remettreLesDonnees() {
        new DataInitializer(emf).initData();
        recherche = new RechercheService(emf);
    }

    @AfterAll
    static void arreter() {
        JpaUtil.fermer();
    }

    protected Salle salle(String code) {
        return recherche.trouverSalleParCode(code);
    }

    protected Utilisateur utilisateur(String adresseMail) {
        return recherche.trouverUtilisateurParMail(adresseMail);
    }
}
