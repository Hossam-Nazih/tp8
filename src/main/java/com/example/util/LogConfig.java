package com.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

/**
 * Charge logging.properties depuis le classpath. java.util.logging ne lit pas ce fichier tout seul :
 * il faut l'appeler en tout premier, avant que Hibernate ne crée ses propres loggers.
 */
public final class LogConfig {

    private static boolean initialise;

    private LogConfig() {}

    public static synchronized void initialiser() {
        if (initialise) {
            return;
        }
        try (InputStream entree = LogConfig.class.getClassLoader().getResourceAsStream("logging.properties")) {
            if (entree != null) {
                LogManager.getLogManager().readConfiguration(entree);
            }
        } catch (IOException e) {
            // Pas de configuration : java.util.logging garde ses réglages par défaut, l'application peut continuer
            System.err.println("logging.properties illisible : " + e.getMessage());
        }
        initialise = true;
    }
}
