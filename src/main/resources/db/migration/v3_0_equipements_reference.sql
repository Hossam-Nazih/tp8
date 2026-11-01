-- Migration version 3.0 : données de référence.
-- Les équipements ne sont jamais modifiés par l'application (entité @Immutable, cache READ_ONLY) :
-- ils sont donc livrés avec le schéma plutôt que créés par le code Java.
INSERT INTO equipements (libelle) VALUES ('Vidéoprojecteur');
INSERT INTO equipements (libelle) VALUES ('Tableau blanc');
INSERT INTO equipements (libelle) VALUES ('Visioconférence');
INSERT INTO equipements (libelle) VALUES ('Climatisation');
INSERT INTO equipements (libelle) VALUES ('Postes informatiques');
