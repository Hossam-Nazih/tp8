-- Migration version 1.0 : schéma initial de la gestion des réservations de salles.
-- Compatible MySQL 8 et H2 en mode MySQL (utilisé par les tests).

-- Un bâtiment regroupe plusieurs salles
CREATE TABLE batiments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom_batiment   VARCHAR(80)  NOT NULL UNIQUE,
    adresse        VARCHAR(200),
    nombre_etages  INT          NOT NULL
);

CREATE TABLE salles (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code_salle     VARCHAR(20)  NOT NULL UNIQUE,
    capacite_max   INT          NOT NULL,
    numero_etage   INT          NOT NULL,
    batiment_id    BIGINT       NOT NULL,
    version_ligne  INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_salle_batiment FOREIGN KEY (batiment_id) REFERENCES batiments (id)
);

-- Liste fixe d'équipements (remplie par la migration 3.0)
CREATE TABLE equipements (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    libelle  VARCHAR(60) NOT NULL UNIQUE
);

-- Table de jointure de la relation ManyToMany Salle <-> Equipement
CREATE TABLE salle_equipements (
    salle_id       BIGINT NOT NULL,
    equipement_id  BIGINT NOT NULL,
    PRIMARY KEY (salle_id, equipement_id),
    CONSTRAINT fk_se_salle      FOREIGN KEY (salle_id)      REFERENCES salles (id),
    CONSTRAINT fk_se_equipement FOREIGN KEY (equipement_id) REFERENCES equipements (id)
);

-- L'unicité de l'adresse mail est ajoutée par la migration 2.0
CREATE TABLE utilisateurs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom_prenom     VARCHAR(100) NOT NULL,
    adresse_mail   VARCHAR(120) NOT NULL,
    telephone      VARCHAR(20),
    departement    VARCHAR(60),
    version_ligne  INT          NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    salle_id             BIGINT       NOT NULL,
    utilisateur_id       BIGINT       NOT NULL,
    debut_creneau        DATETIME     NOT NULL,
    fin_creneau          DATETIME     NOT NULL,
    nombre_participants  INT          NOT NULL,
    motif                VARCHAR(200) NOT NULL,
    statut               VARCHAR(20)  NOT NULL,
    date_creation        DATETIME     NOT NULL,
    version_ligne        INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_reservation_salle       FOREIGN KEY (salle_id)       REFERENCES salles (id),
    CONSTRAINT fk_reservation_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs (id),
    -- Dernier garde-fou si un programme contourne la vérification faite par ReservationService
    CONSTRAINT ck_creneau_valide CHECK (fin_creneau > debut_creneau)
);

-- La recherche de conflits filtre toujours sur la salle puis sur les dates
CREATE INDEX idx_reservation_salle_debut ON reservations (salle_id, debut_creneau);
