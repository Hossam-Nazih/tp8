package com.example.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Paramètres de connexion lus dans database.properties, chacun remplaçable par -Ddb.xxx=... au lancement.
 * JPA et les migrations JDBC utilisent tous deux cette classe : impossible qu'ils visent deux bases différentes.
 */
public final class ConfigurationBD {

    private static final String FICHIER = "database.properties";

    private final String pilote;
    private final String url;
    private final String utilisateur;
    private final String motDePasse;
    private final String dialecte;

    private ConfigurationBD(Properties proprietes) {
        this.pilote = lire(proprietes, "db.driver");
        this.url = lire(proprietes, "db.url");
        this.utilisateur = lire(proprietes, "db.user");
        this.motDePasse = lire(proprietes, "db.password");
        this.dialecte = lire(proprietes, "db.dialect");
    }

    public static ConfigurationBD charger() {
        Properties proprietes = new Properties();
        try (InputStream entree = ConfigurationBD.class.getClassLoader().getResourceAsStream(FICHIER)) {
            if (entree == null) {
                throw new IllegalStateException(FICHIER + " introuvable dans le classpath");
            }
            proprietes.load(entree);
        } catch (IOException e) {
            throw new IllegalStateException("Lecture de " + FICHIER + " impossible", e);
        }
        return new ConfigurationBD(proprietes);
    }

    // Propriétés ajoutées à celles de persistence.xml à la création de l'EntityManagerFactory
    public Map<String, String> proprietesJpa() {
        Map<String, String> jpa = new HashMap<>();
        jpa.put("javax.persistence.jdbc.driver", pilote);
        jpa.put("javax.persistence.jdbc.url", url);
        jpa.put("javax.persistence.jdbc.user", utilisateur);
        jpa.put("javax.persistence.jdbc.password", motDePasse);
        jpa.put("hibernate.dialect", dialecte);
        // Les tests coupent l'affichage du SQL avec -Dhibernate.show_sql=false
        String afficherSql = System.getProperty("hibernate.show_sql");
        if (afficherSql != null) {
            jpa.put("hibernate.show_sql", afficherSql);
        }
        return jpa;
    }

    public String getUrl() { return url; }
    public String getUtilisateur() { return utilisateur; }
    public String getMotDePasse() { return motDePasse; }

    // Une propriété système l'emporte sur le fichier ; un mot de passe vide est une valeur valide (root "" sous Workbench)
    private static String lire(Properties proprietes, String cle) {
        String valeur = System.getProperty(cle, proprietes.getProperty(cle));
        if (valeur == null) {
            throw new IllegalStateException("Propriété " + cle + " absente de " + FICHIER);
        }
        return valeur.trim();
    }
}
