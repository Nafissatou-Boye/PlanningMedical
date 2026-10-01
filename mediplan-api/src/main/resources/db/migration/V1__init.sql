-- =====================================================================
--  MediPlan — V1 : création du schéma
--  MySQL 8.0 (Laragon) — InnoDB, utf8mb4
--  Correspond au diagramme de classes V1.
-- =====================================================================

-- ---------------------------------------------------------------------
--  1. PARAMÉTRAGE
-- ---------------------------------------------------------------------

-- En-tête officiel des PDF (une seule ligne)
CREATE TABLE etablissement (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    republique   VARCHAR(120) NOT NULL,
    devise       VARCHAR(120) NOT NULL,
    ministere    VARCHAR(200) NOT NULL,
    nom          VARCHAR(200) NOT NULL,
    ville        VARCHAR(100) NOT NULL,
    logo_chemin  VARCHAR(255) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE service (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    nom                 VARCHAR(150) NOT NULL,
    code                VARCHAR(30)  NULL,
    reference_document  VARCHAR(100) NULL COMMENT 'Ex. MSAS/CHNP/ANESTH-REA',
    ordre_affichage     INT          NOT NULL DEFAULT 0,
    actif               BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_service_nom  UNIQUE (nom),
    CONSTRAINT uk_service_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Groupe de colonnes du tableau (REA, Bloc central, Bloc PME...)
CREATE TABLE sous_service (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    service_id       BIGINT       NOT NULL,
    nom              VARCHAR(150) NOT NULL,
    ordre_affichage  INT          NOT NULL DEFAULT 0,
    actif            BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_sous_service_nom UNIQUE (service_id, nom),
    CONSTRAINT fk_sous_service_service FOREIGN KEY (service_id) REFERENCES service (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Colonne du tableau : une plage horaire d'un sous-service (08H-16H, 16H-08H...)
CREATE TABLE plage_horaire (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    sous_service_id   BIGINT      NOT NULL,
    libelle           VARCHAR(30) NOT NULL,
    heure_debut       TIME        NOT NULL,
    heure_fin         TIME        NOT NULL COMMENT 'Si heure_fin <= heure_debut, la plage finit le lendemain',
    type_garde        VARCHAR(10) NOT NULL,
    mode_par_defaut   VARCHAR(10) NOT NULL DEFAULT 'UNIQUE',
    ordre_affichage   INT         NOT NULL DEFAULT 0,
    actif             BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_plage_libelle UNIQUE (sous_service_id, libelle),
    CONSTRAINT ck_plage_type CHECK (type_garde IN ('JOUR', 'NUIT', 'H24')),
    CONSTRAINT ck_plage_mode CHECK (mode_par_defaut IN ('UNIQUE', 'BINOME')),
    CONSTRAINT fk_plage_sous_service FOREIGN KEY (sous_service_id) REFERENCES sous_service (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Postes du planning des stagiaires (PME/2nd VPA, Salle Prog.1, VPA...)
CREATE TABLE poste_stage (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    service_id       BIGINT       NOT NULL,
    libelle          VARCHAR(80)  NOT NULL,
    jours_actifs     VARCHAR(40)  NULL COMMENT 'Ex. MARDI,MERCREDI ; vide = toute la période',
    partiel          BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'Poste tenu en plus d un poste principal (ex. VPA)',
    couleur          VARCHAR(7)   NULL,
    ordre_affichage  INT          NOT NULL DEFAULT 0,
    actif            BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_poste_stage_libelle UNIQUE (service_id, libelle),
    CONSTRAINT fk_poste_stage_service FOREIGN KEY (service_id) REFERENCES service (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Signatures en bas du PDF (Le Chef de Service, Le Chef RH, Le Directeur)
CREATE TABLE signataire (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    service_id       BIGINT       NOT NULL,
    fonction         VARCHAR(100) NOT NULL,
    ordre_affichage  INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_signataire_service FOREIGN KEY (service_id) REFERENCES service (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE jour_ferie (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    date_ferie  DATE         NOT NULL,
    libelle     VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_jour_ferie_date UNIQUE (date_ferie)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
--  2. PERSONNEL (personnel soignant et stagiaires : une seule table agent)
-- ---------------------------------------------------------------------

CREATE TABLE profession (
    id       BIGINT       NOT NULL AUTO_INCREMENT,
    libelle  VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_profession_libelle UNIQUE (libelle)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE agent (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    matricule       VARCHAR(30)  NULL,
    titre           VARCHAR(10)  NULL COMMENT 'Ex. Dr',
    prenom          VARCHAR(80)  NOT NULL,
    nom             VARCHAR(80)  NOT NULL,
    nom_affichage   VARCHAR(60)  NOT NULL COMMENT 'Tel qu affiché dans le PDF : Dr Bara Aw, Adama...',
    email           VARCHAR(150) NULL,
    telephone       VARCHAR(30)  NULL,
    categorie       VARCHAR(12)  NOT NULL DEFAULT 'PERSONNEL',
    profession_id   BIGINT       NULL,
    actif           BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_agent_matricule UNIQUE (matricule),
    CONSTRAINT ck_agent_categorie CHECK (categorie IN ('PERSONNEL', 'STAGIAIRE')),
    CONSTRAINT fk_agent_profession FOREIGN KEY (profession_id) REFERENCES profession (id),
    INDEX idx_agent_nom (nom, prenom)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Un agent peut être rattaché à plusieurs services
CREATE TABLE agent_service (
    agent_id    BIGINT NOT NULL,
    service_id  BIGINT NOT NULL,
    PRIMARY KEY (agent_id, service_id),
    CONSTRAINT fk_agent_service_agent   FOREIGN KEY (agent_id)   REFERENCES agent (id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_service_service FOREIGN KEY (service_id) REFERENCES service (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Période de stage d'un stagiaire : hors de ces dates, il n'est proposé nulle part
CREATE TABLE stage (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id               BIGINT       NOT NULL,
    service_id             BIGINT       NOT NULL,
    niveau                 VARCHAR(50)  NULL COMMENT 'DES, interne, externe...',
    etablissement_origine  VARCHAR(150) NULL,
    date_debut             DATE         NOT NULL,
    date_fin               DATE         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_stage_dates CHECK (date_fin >= date_debut),
    CONSTRAINT fk_stage_agent   FOREIGN KEY (agent_id)   REFERENCES agent (id) ON DELETE CASCADE,
    CONSTRAINT fk_stage_service FOREIGN KEY (service_id) REFERENCES service (id),
    INDEX idx_stage_periode (agent_id, date_debut, date_fin)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Congés et indisponibilités (saisis une seule fois, appliqués partout)
CREATE TABLE conge (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id         BIGINT       NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    date_debut       DATE         NOT NULL,
    date_fin         DATE         NOT NULL,
    motif            VARCHAR(255) NULL,
    affiche_sur_pdf  BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT ck_conge_dates CHECK (date_fin >= date_debut),
    CONSTRAINT ck_conge_type CHECK (type IN ('ANNUEL', 'MALADIE', 'MATERNITE', 'FORMATION', 'INDISPONIBILITE', 'AUTRE')),
    CONSTRAINT fk_conge_agent FOREIGN KEY (agent_id) REFERENCES agent (id) ON DELETE CASCADE,
    INDEX idx_conge_periode (agent_id, date_debut, date_fin),
    INDEX idx_conge_dates (date_debut, date_fin)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
--  3. PLANNINGS
-- ---------------------------------------------------------------------

CREATE TABLE planning (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    service_id        BIGINT       NOT NULL,
    type              VARCHAR(12)  NOT NULL,
    mois              TINYINT      NOT NULL,
    annee             SMALLINT     NOT NULL,
    statut            VARCHAR(12)  NOT NULL DEFAULT 'BROUILLON',
    version           INT          NOT NULL DEFAULT 1,
    date_document     DATE         NULL COMMENT 'Pikine, le ...',
    numero_reference  VARCHAR(100) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_planning UNIQUE (service_id, type, annee, mois),
    CONSTRAINT ck_planning_type CHECK (type IN ('GARDES', 'STAGIAIRES')),
    CONSTRAINT ck_planning_statut CHECK (statut IN ('BROUILLON', 'VALIDE', 'PUBLIE')),
    CONSTRAINT ck_planning_mois CHECK (mois BETWEEN 1 AND 12),
    CONSTRAINT fk_planning_service FOREIGN KEY (service_id) REFERENCES service (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Une case du tableau des gardes : une date + une plage horaire
CREATE TABLE creneau (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    planning_id       BIGINT      NOT NULL,
    plage_horaire_id  BIGINT      NOT NULL,
    date_garde        DATE        NOT NULL,
    debut             DATETIME    NOT NULL,
    fin               DATETIME    NOT NULL,
    mode              VARCHAR(10) NOT NULL DEFAULT 'UNIQUE',
    PRIMARY KEY (id),
    -- VERROU 1 : une case n'existe qu'une seule fois
    CONSTRAINT uk_creneau UNIQUE (plage_horaire_id, date_garde),
    CONSTRAINT ck_creneau_mode CHECK (mode IN ('UNIQUE', 'BINOME')),
    CONSTRAINT ck_creneau_horaires CHECK (fin > debut),
    CONSTRAINT fk_creneau_planning FOREIGN KEY (planning_id) REFERENCES planning (id) ON DELETE CASCADE,
    CONSTRAINT fk_creneau_plage    FOREIGN KEY (plage_horaire_id) REFERENCES plage_horaire (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Agent placé dans une case (1 place en Unique, 2 en Binôme)
CREATE TABLE affectation (
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    creneau_id   BIGINT   NOT NULL,
    agent_id     BIGINT   NOT NULL,
    position     TINYINT  NOT NULL DEFAULT 1,
    debut        DATETIME NOT NULL COMMENT 'Copie des horaires du créneau, pour le contrôle de chevauchement',
    fin          DATETIME NOT NULL,
    PRIMARY KEY (id),
    -- VERROU 2 : une place prise ne peut plus être donnée à un autre agent
    CONSTRAINT uk_affectation_place UNIQUE (creneau_id, position),
    -- Un agent n'occupe pas deux places de la même case
    CONSTRAINT uk_affectation_agent UNIQUE (creneau_id, agent_id),
    CONSTRAINT ck_affectation_position CHECK (position IN (1, 2)),
    CONSTRAINT fk_affectation_creneau FOREIGN KEY (creneau_id) REFERENCES creneau (id) ON DELETE CASCADE,
    CONSTRAINT fk_affectation_agent   FOREIGN KEY (agent_id)   REFERENCES agent (id),
    -- VERROU 3 (contrôlé par l'application, en transaction) : pas de chevauchement pour un même agent
    INDEX idx_affectation_agent_horaires (agent_id, debut, fin)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Astreinte : un agent par période (du 1er au premier dimanche, puis lundi-dimanche)
CREATE TABLE astreinte (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    planning_id  BIGINT NOT NULL,
    agent_id     BIGINT NOT NULL,
    date_debut   DATE   NOT NULL,
    date_fin     DATE   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_astreinte_periode UNIQUE (planning_id, date_debut),
    CONSTRAINT ck_astreinte_dates CHECK (date_fin >= date_debut),
    CONSTRAINT fk_astreinte_planning FOREIGN KEY (planning_id) REFERENCES planning (id) ON DELETE CASCADE,
    CONSTRAINT fk_astreinte_agent    FOREIGN KEY (agent_id)    REFERENCES agent (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Ligne du planning des stagiaires : dates choisies par le planificateur
CREATE TABLE periode_stage (
    id           BIGINT  NOT NULL AUTO_INCREMENT,
    planning_id  BIGINT  NOT NULL,
    date_debut   DATE    NOT NULL,
    date_fin     DATE    NOT NULL,
    ordre        INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_periode_stage UNIQUE (planning_id, date_debut),
    CONSTRAINT ck_periode_stage_dates CHECK (date_fin >= date_debut),
    CONSTRAINT fk_periode_stage_planning FOREIGN KEY (planning_id) REFERENCES planning (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Case du planning des stagiaires : une période + un poste (1 ou 2 stagiaires)
CREATE TABLE affectation_stage (
    id                BIGINT  NOT NULL AUTO_INCREMENT,
    periode_stage_id  BIGINT  NOT NULL,
    poste_stage_id    BIGINT  NOT NULL,
    agent_id          BIGINT  NOT NULL,
    position          TINYINT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    -- Même verrou que pour les gardes : une place prise est bloquée
    CONSTRAINT uk_affectation_stage_place UNIQUE (periode_stage_id, poste_stage_id, position),
    CONSTRAINT uk_affectation_stage_agent UNIQUE (periode_stage_id, poste_stage_id, agent_id),
    CONSTRAINT ck_affectation_stage_position CHECK (position IN (1, 2)),
    CONSTRAINT fk_affectation_stage_periode FOREIGN KEY (periode_stage_id) REFERENCES periode_stage (id) ON DELETE CASCADE,
    CONSTRAINT fk_affectation_stage_poste   FOREIGN KEY (poste_stage_id)   REFERENCES poste_stage (id),
    CONSTRAINT fk_affectation_stage_agent   FOREIGN KEY (agent_id)         REFERENCES agent (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
--  4. DIFFUSION ET SÉCURITÉ
-- ---------------------------------------------------------------------

CREATE TABLE envoi_pdf (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    planning_id         BIGINT       NOT NULL,
    agent_id            BIGINT       NULL,
    email_destinataire  VARCHAR(150) NOT NULL,
    date_envoi          DATETIME     NOT NULL,
    statut              VARCHAR(12)  NOT NULL DEFAULT 'EN_ATTENTE',
    message_erreur      VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_envoi_statut CHECK (statut IN ('EN_ATTENTE', 'ENVOYE', 'ECHEC')),
    CONSTRAINT fk_envoi_planning FOREIGN KEY (planning_id) REFERENCES planning (id) ON DELETE CASCADE,
    CONSTRAINT fk_envoi_agent    FOREIGN KEY (agent_id)    REFERENCES agent (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Compte de l'administrateur (utilisateur unique de l'application)
CREATE TABLE utilisateur (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    nom                 VARCHAR(80)  NOT NULL,
    prenom              VARCHAR(80)  NOT NULL,
    nom_utilisateur     VARCHAR(30)  NOT NULL,
    mot_de_passe_hache  VARCHAR(100) NOT NULL,
    email               VARCHAR(150) NULL,
    poste               VARCHAR(80)  NOT NULL,
    service_id          BIGINT       NULL,
    actif               BOOLEAN      NOT NULL DEFAULT TRUE,
    date_creation       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_utilisateur_nom_utilisateur UNIQUE (nom_utilisateur),
    CONSTRAINT fk_utilisateur_service FOREIGN KEY (service_id) REFERENCES service (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE journal_audit (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    utilisateur_id  BIGINT       NULL,
    date_action     DATETIME     NOT NULL,
    action          VARCHAR(50)  NOT NULL,
    entite          VARCHAR(50)  NOT NULL,
    entite_id       BIGINT       NULL,
    details         TEXT         NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id) ON DELETE SET NULL,
    INDEX idx_audit_entite (entite, entite_id),
    INDEX idx_audit_date (date_action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
