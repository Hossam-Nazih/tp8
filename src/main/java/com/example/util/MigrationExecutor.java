package com.example.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Applique les scripts SQL de db/migration via JDBC, avant le démarrage de JPA.
 *
 * Chaque script appliqué est noté dans la table historique_migrations : relancer l'application
 * ne rejoue rien, et seuls les nouveaux scripts ajoutés à index.txt sont exécutés.
 */
public class MigrationExecutor {

    private static final Logger LOGGER = Logger.getLogger(MigrationExecutor.class.getName());

    private static final String DOSSIER = "db/migration/";
    private static final String INDEX = DOSSIER + "index.txt";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public MigrationExecutor(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public MigrationExecutor(ConfigurationBD config) {
        this(config.getUrl(), config.getUtilisateur(), config.getMotDePasse());
    }

    /** Applique, dans l'ordre d'index.txt, les scripts pas encore appliqués. Renvoie leur nombre. */
    public int executerToutes() throws SQLException, IOException {
        int appliques = 0;
        for (String script : lignesUtiles(INDEX)) {
            if (executeMigration(DOSSIER + script)) {
                appliques++;
            }
        }
        LOGGER.info(appliques == 0 ? "Schéma à jour, aucune migration à appliquer"
                                   : appliques + " migration(s) appliquée(s)");
        return appliques;
    }

    /** Applique un script s'il ne l'a pas encore été. Renvoie false s'il figurait déjà dans l'historique. */
    public boolean executeMigration(String scriptPath) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            creerHistoriqueSiAbsent(conn);
            if (dejaApplique(conn, scriptPath)) {
                return false;
            }

            List<String> instructions = decouper(lireRessource(scriptPath));
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Une instruction à la fois : MySQL refuse plusieurs instructions dans un seul execute()
                for (String instruction : instructions) {
                    stmt.execute(instruction);
                }
                noterDansHistorique(conn, scriptPath);
                conn.commit();
                LOGGER.info("Migration appliquée : " + scriptPath + " (" + instructions.size() + " instructions)");
                return true;
            } catch (SQLException e) {
                // Attention : MySQL valide implicitement chaque CREATE / ALTER, le rollback n'annule que les INSERT.
                // Un script de schéma qui échoue au milieu doit donc être corrigé à la main avant de relancer.
                conn.rollback();
                LOGGER.severe("Échec de la migration " + scriptPath + " : " + e.getMessage());
                throw e;
            }
        }
    }

    private static void creerHistoriqueSiAbsent(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS historique_migrations ("
                    + "script VARCHAR(150) PRIMARY KEY, "
                    + "date_application DATETIME NOT NULL)");
        }
    }

    private static boolean dejaApplique(Connection conn, String script) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM historique_migrations WHERE script = ?")) {
            ps.setString(1, script);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void noterDansHistorique(Connection conn, String script) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO historique_migrations (script, date_application) VALUES (?, ?)")) {
            ps.setString(1, script);
            ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    // Coupe sur les ";" les lignes déjà débarrassées des commentaires "--" (aucun script ne met de ";" dans un texte)
    private static List<String> decouper(List<String> lignes) {
        StringBuilder sansCommentaires = new StringBuilder();
        for (String ligne : lignes) {
            sansCommentaires.append(ligne).append('\n');
        }
        List<String> instructions = new ArrayList<>();
        for (String morceau : sansCommentaires.toString().split(";")) {
            if (!morceau.trim().isEmpty()) {
                instructions.add(morceau.trim());
            }
        }
        return instructions;
    }

    private static List<String> lireRessource(String chemin) throws IOException {
        InputStream is = MigrationExecutor.class.getClassLoader().getResourceAsStream(chemin);
        if (is == null) {
            throw new IOException("Script introuvable dans le classpath : " + chemin);
        }
        List<String> lignes = new ArrayList<>();
        // UTF-8 explicite : sinon Windows lirait les accents des scripts avec son encodage par défaut
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String ligne;
            while ((ligne = reader.readLine()) != null) {
                if (!ligne.trim().startsWith("--")) {
                    lignes.add(ligne);
                }
            }
        }
        return lignes;
    }

    // Lignes de index.txt hors commentaires "#" et lignes vides
    private static List<String> lignesUtiles(String chemin) throws IOException {
        List<String> scripts = new ArrayList<>();
        for (String ligne : lireRessource(chemin)) {
            String nom = ligne.trim();
            if (!nom.isEmpty() && !nom.startsWith("#")) {
                scripts.add(nom);
            }
        }
        return scripts;
    }
}
