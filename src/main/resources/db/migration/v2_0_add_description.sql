-- Migration version 2.0 (script de l'énoncé, étape 5)

-- Texte libre affiché aux utilisateurs pour décrire la salle (lumière, accès...)
ALTER TABLE salles ADD COLUMN description VARCHAR(255);

-- Deux comptes ne peuvent pas partager la même adresse mail
CREATE UNIQUE INDEX idx_utilisateur_email ON utilisateurs (adresse_mail);
