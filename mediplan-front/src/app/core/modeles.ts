// Types échangés avec l'API (mêmes noms que les DTO Java).

export type TypeGarde = 'JOUR' | 'NUIT' | 'H24';
export type ModeAffectation = 'UNIQUE' | 'BINOME';
export type CategorieAgent = 'PERSONNEL' | 'STAGIAIRE';
export type TypeConge = 'ANNUEL' | 'MALADIE' | 'MATERNITE' | 'FORMATION' | 'INDISPONIBILITE' | 'AUTRE';

/** Dates échangées au format ISO « AAAA-MM-JJ », heures au format « HH:mm:ss ». */
export type DateIso = string;

export interface Plage {
  id: number;
  libelle: string;
  heureDebut: string;
  heureFin: string;
  typeGarde: TypeGarde;
  modeParDefaut: ModeAffectation;
  ordreAffichage: number;
  actif: boolean;
  finitLeLendemain: boolean;
}

export interface SousService {
  id: number;
  nom: string;
  ordreAffichage: number;
  actif: boolean;
  plages: Plage[];
}

export interface PosteStage {
  id: number;
  libelle: string;
  joursActifs: string | null;
  partiel: boolean;
  couleur: string | null;
  ordreAffichage: number;
  actif: boolean;
}

export interface Service {
  id: number;
  nom: string;
  code: string | null;
  referenceDocument: string | null;
  ordreAffichage: number;
  actif: boolean;
  sousServices: SousService[];
  postesStage: PosteStage[];
}

export interface Reference {
  id: number;
  libelle: string;
}

export interface Stage {
  id: number;
  niveau: string | null;
  etablissementOrigine: string | null;
  serviceId: number;
  service: string;
  dateDebut: DateIso;
  dateFin: DateIso;
  enCours: boolean;
}

export interface Agent {
  id: number;
  matricule: string | null;
  titre: string | null;
  prenom: string;
  nom: string;
  nomAffichage: string;
  email: string | null;
  telephone: string | null;
  categorie: CategorieAgent;
  professionId: number | null;
  profession: string | null;
  services: Reference[];
  actif: boolean;
  stage: Stage | null;
}

export interface AgentRequete {
  titre: string | null;
  prenom: string;
  nom: string;
  nomAffichage: string | null;
  matricule: string | null;
  email: string | null;
  telephone: string | null;
  categorie: CategorieAgent;
  professionId: number | null;
  serviceIds: number[];
  stage: {
    niveau: string | null;
    etablissementOrigine: string | null;
    serviceId: number | null;
    dateDebut: DateIso;
    dateFin: DateIso;
  } | null;
  actif?: boolean;
}

export interface Conge {
  id: number;
  agentId: number;
  agent: string;
  agentNomComplet: string;
  categorie: CategorieAgent;
  type: TypeConge;
  dateDebut: DateIso;
  dateFin: DateIso;
  nombreJours: number;
  motif: string | null;
  afficheSurPdf: boolean;
}

export interface CongeRequete {
  agentId: number;
  type: TypeConge;
  dateDebut: DateIso;
  dateFin: DateIso;
  motif: string | null;
}

// ---------- Libellés affichés ----------

export const LIBELLES_TYPE_GARDE: Record<TypeGarde, string> = { JOUR: 'Jour', NUIT: 'Nuit', H24: '24h' };
export const CLASSES_TYPE_GARDE: Record<TypeGarde, string> = { JOUR: 'jour', NUIT: 'nuit', H24: 'h24' };
export const LIBELLES_MODE: Record<ModeAffectation, string> = { UNIQUE: 'Unique', BINOME: 'Binôme' };
export const LIBELLES_TYPE_CONGE: Record<TypeConge, string> = {
  ANNUEL: 'Congé annuel',
  MALADIE: 'Maladie',
  MATERNITE: 'Maternité',
  FORMATION: 'Formation',
  INDISPONIBILITE: 'Indisponibilité',
  AUTRE: 'Autre',
};
