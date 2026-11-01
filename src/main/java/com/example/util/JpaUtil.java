package com.example.util;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * Une seule EntityManagerFactory pour toute l'application : sa création est coûteuse,
 * et c'est elle qui porte le cache de second niveau partagé par tous les EntityManager.
 */
public final class JpaUtil {

    private static final String UNITE_PERSISTENCE = "ReservationPU";

    private static EntityManagerFactory emf;

    private JpaUtil() {}

    // Les tables doivent déjà exister (MigrationExecutor) : avec hbm2ddl=validate, Hibernate refuse de démarrer sinon
    public static synchronized EntityManagerFactory getEntityManagerFactory() {
        if (emf == null || !emf.isOpen()) {
            emf = Persistence.createEntityManagerFactory(UNITE_PERSISTENCE, ConfigurationBD.charger().proprietesJpa());
        }
        return emf;
    }

    public static synchronized void fermer() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
        emf = null;
    }
}
