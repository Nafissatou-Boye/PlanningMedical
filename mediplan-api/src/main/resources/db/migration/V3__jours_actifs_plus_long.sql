-- Les 7 jours cochés (« LUNDI,MARDI,...,DIMANCHE ») font 50 caractères : on passe la colonne à 80.
ALTER TABLE poste_stage
    MODIFY jours_actifs VARCHAR(80) NULL COMMENT 'Ex. MARDI,MERCREDI ; vide = toute la période';
