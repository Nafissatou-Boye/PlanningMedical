/** Une entrée du menu latéral. */
export interface ElementMenu {
  libelle: string;
  icone: string; // nom d'icône Material Symbols
  lien: string;
}

/** Menu principal « Espace de travail ». */
export const MENU_PRINCIPAL: ElementMenu[] = [
  { libelle: 'Tableau de bord', icone: 'grid_view', lien: '/tableau-de-bord' },
  { libelle: 'Planning des gardes', icone: 'calendar_month', lien: '/planning' },
  { libelle: 'Stagiaires', icone: 'school', lien: '/stagiaires' },
  { libelle: 'Personnel', icone: 'group', lien: '/personnel' },
  { libelle: 'Congés', icone: 'beach_access', lien: '/conges' },
  { libelle: 'Services', icone: 'medical_services', lien: '/services' },
  { libelle: 'Rapports', icone: 'bar_chart', lien: '/rapports' },
];

/** Menu secondaire, en bas de la barre latérale. */
export const MENU_SECONDAIRE: ElementMenu[] = [
  { libelle: "Centre d'aide", icone: 'help', lien: '/aide' },
  { libelle: 'Paramètres', icone: 'settings', lien: '/parametres' },
];
