-- =====================================================================
--  MediPlan — V2 : données de départ
-- =====================================================================

-- En-tête officiel repris du document du CHN de Pikine
INSERT INTO etablissement (republique, devise, ministere, nom, ville, logo_chemin)
VALUES ('REPUBLIQUE DU SENEGAL',
        'Un Peuple – Un But – Une Foi',
        'Ministère de la Santé et de l''Action Sociale',
        'Centre Hospitalier National De Pikine',
        'Pikine',
        'pdf/logo-ministere.jpg');

-- Professions courantes (modifiables ensuite dans Paramètres)
INSERT INTO profession (libelle) VALUES
    ('Médecin'),
    ('Infirmier(ère)'),
    ('Sage-femme'),
    ('Aide-soignant(e)'),
    ('Technicien(ne) supérieur(e)'),
    ('Étudiant(e) stagiaire');
