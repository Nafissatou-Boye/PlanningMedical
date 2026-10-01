/** Questions fréquentes du Centre d'aide. Le texte accepte <strong> et <br>. */
export interface Question {
  theme: Theme;
  question: string;
  reponse: string;
  lien?: { libelle: string; route: string };
}

export type Theme = 'Planning' | 'Personnel' | 'Stagiaires' | 'PDF et e-mail' | 'Paramètres';

export const THEMES: { theme: Theme; icone: string }[] = [
  { theme: 'Planning', icone: 'calendar_month' },
  { theme: 'Personnel', icone: 'group' },
  { theme: 'Stagiaires', icone: 'school' },
  { theme: 'PDF et e-mail', icone: 'picture_as_pdf' },
  { theme: 'Paramètres', icone: 'settings' },
];

export const QUESTIONS: Question[] = [
  // ---------- Planning ----------
  {
    theme: 'Planning',
    question: 'Comment préparer le planning d\'un mois ?',
    reponse:
      'Dans <strong>Planning des gardes</strong>, choisissez le mois avec les flèches puis cliquez sur <strong>Créer le planning</strong>. ' +
      'Cliquez ensuite sur une case pour y placer un agent. Le tableau reprend automatiquement les sous-services et les plages horaires définis dans « Services ».',
    lien: { libelle: 'Ouvrir le planning', route: '/planning' },
  },
  {
    theme: 'Planning',
    question: 'Pourquoi un agent n\'est-il pas proposé pour une case ?',
    reponse:
      'Seuls les agents <strong>disponibles</strong> sont proposés. Un agent est écarté s\'il est en congé ou indisponible ce jour-là, ' +
      's\'il est déjà de garde sur un horaire qui se chevauche (même dans un autre service), s\'il est désactivé, s\'il n\'est pas rattaché au service, ' +
      'ou, pour un stagiaire, s\'il est hors de sa période de stage.<br>Cliquez sur <strong>Voir les agents indisponibles</strong> dans la fenêtre pour lire la raison exacte.',
  },
  {
    theme: 'Planning',
    question: 'Comment donner une place déjà prise à quelqu\'un d\'autre ?',
    reponse:
      'Une place attribuée est <strong>verrouillée</strong> : personne ne peut l\'écraser par erreur. Cliquez sur la case, puis sur ' +
      '<strong>Libérer la place</strong>. Elle redevient libre et vous pouvez choisir un autre agent.',
  },
  {
    theme: 'Planning',
    question: 'Quelle différence entre « Unique » et « Binôme » ?',
    reponse:
      '<strong>Unique</strong> : une seule personne dans la case. <strong>Binôme</strong> : deux personnes (affichées « A / B »). ' +
      'Le mode par défaut se règle pour chaque plage horaire dans « Services », et vous pouvez le changer case par case dans la fenêtre d\'affectation.',
  },
  {
    theme: 'Planning',
    question: 'Comment sont découpées les astreintes ?',
    reponse:
      'Du <strong>1er du mois jusqu\'au premier dimanche</strong>, puis du <strong>lundi au dimanche</strong>, la dernière période s\'arrêtant à la fin du mois. ' +
      'Cliquez sur une cellule de la colonne ASTREINTES pour y placer un agent.',
  },
  {
    theme: 'Planning',
    question: 'Que signifie « Brouillon » et « Publié » ?',
    reponse:
      'Un planning en <strong>brouillon</strong> est en préparation. <strong>Publier</strong> le marque comme définitif et met à jour la date du document. ' +
      'Vous pouvez toujours le repasser en brouillon pour le corriger.',
  },
  // ---------- Personnel ----------
  {
    theme: 'Personnel',
    question: 'Comment ajouter un congé ?',
    reponse:
      'Dans <strong>Congés</strong>, cliquez sur <strong>Ajouter un congé</strong>, choisissez l\'agent, le type et les dates. ' +
      'Il est appliqué <strong>partout automatiquement</strong> : l\'agent n\'est plus proposé sur ces dates, et le congé apparaît dans le bloc « CONGÉS » du PDF. ' +
      'Le type <strong>Indisponibilité</strong> bloque l\'agent sans l\'afficher dans le PDF.',
    lien: { libelle: 'Gérer les congés', route: '/conges' },
  },
  {
    theme: 'Personnel',
    question: 'Un agent quitte le service : faut-il le supprimer ?',
    reponse:
      'Non : ouvrez sa fiche et <strong>désactivez-le</strong>. Il n\'est plus proposé dans les plannings, mais ses gardes passées restent dans l\'historique et les rapports.',
    lien: { libelle: 'Ouvrir le personnel', route: '/personnel' },
  },
  {
    theme: 'Personnel',
    question: 'Quel nom apparaît dans le planning et le PDF ?',
    reponse:
      'Le <strong>nom affiché</strong> de la fiche (ex. « Dr Ndaw »). Par défaut il reprend titre, prénom et nom ; vous pouvez le raccourcir pour que le PDF reste lisible.',
  },
  // ---------- Stagiaires ----------
  {
    theme: 'Stagiaires',
    question: 'Comment organiser les stagiaires du mois ?',
    reponse:
      'Dans <strong>Stagiaires › Organisation du mois</strong>, créez l\'organisation : les semaines sont proposées du <strong>lundi au dimanche</strong> ' +
      '(une semaine appartient au mois qui contient son mercredi). Cliquez sur une semaine pour changer ses dates, et sur une case pour y placer jusqu\'à <strong>deux stagiaires</strong>.',
    lien: { libelle: 'Ouvrir les stagiaires', route: '/stagiaires' },
  },
  {
    theme: 'Stagiaires',
    question: 'Pourquoi un stagiaire ne peut-il pas être placé sur deux postes la même semaine ?',
    reponse:
      'Un stagiaire n\'a qu\'<strong>un poste principal</strong> par semaine. Seul un poste marqué « <strong>tenu en plus d\'un autre poste</strong> » (comme la VPA le mardi et le mercredi) ' +
      'peut s\'ajouter à son poste principal. Ce réglage se fait dans « Services », rubrique « Postes du planning des stagiaires ».',
  },
  {
    theme: 'Stagiaires',
    question: 'Les stagiaires peuvent-ils faire des gardes ?',
    reponse:
      'Oui. Ils sont proposés dans le planning des gardes comme les autres agents, mais <strong>seulement pendant leur période de stage</strong>.',
  },
  // ---------- PDF et e-mail ----------
  {
    theme: 'PDF et e-mail',
    question: 'Le numéro de référence n\'apparaît pas dans l\'en-tête du PDF',
    reponse:
      'Renseignez la <strong>Référence du document</strong> du service (ex. MSAS/CHNP/ANESTH-REA) dans « Services › Modifier ». ' +
      'Sans référence, le PDF affiche des pointillés à compléter à la main.',
    lien: { libelle: 'Ouvrir les services', route: '/services' },
  },
  {
    theme: 'PDF et e-mail',
    question: 'Comment envoyer le planning par e-mail ?',
    reponse:
      'Dans le planning, cliquez sur <strong>Envoyer</strong>, cochez les destinataires et ajoutez un message si besoin. ' +
      'Chacun reçoit le PDF en pièce jointe et, dans le message, la liste de <strong>ses propres</strong> gardes ou postes. ' +
      'Un agent sans adresse e-mail ne peut pas être coché : complétez sa fiche.',
  },
  {
    theme: 'PDF et e-mail',
    question: 'Comment configurer l\'envoi des e-mails ?',
    reponse:
      'Dans <strong>Paramètres › Envoi des e-mails</strong>, indiquez l\'adresse Gmail qui enverra les plannings et un <strong>mot de passe d\'application</strong> Gmail ' +
      '(compte Google › Sécurité › Validation en deux étapes › Mots de passe des applications : 16 lettres). ' +
      'MediPlan vérifie le compte auprès de Gmail, vous envoie un e-mail de test, et l\'enregistre : c\'est à faire une seule fois.',
    lien: { libelle: 'Configurer l\'envoi', route: '/parametres' },
  },
  {
    theme: 'PDF et e-mail',
    question: 'L\'envoi échoue avec « Authentication failed »',
    reponse:
      'Le mot de passe d\'application est faux ou a été révoqué. Créez-en un nouveau dans votre compte Google, puis ' +
      '<strong>Paramètres › Envoi des e-mails › Changer de compte</strong>. Ne partagez jamais ce mot de passe.',
  },
  // ---------- Paramètres ----------
  {
    theme: 'Paramètres',
    question: 'Puis-je utiliser MediPlan sur mon téléphone ?',
    reponse:
      'Oui, si le téléphone est connecté au <strong>même Wi-Fi</strong> que l\'ordinateur où MediPlan est démarré. ' +
      'Ouvrez <strong>Paramètres › Ouvrir MediPlan sur le téléphone</strong> et scannez le QR code, puis connectez-vous. ' +
      'Hors de ce Wi-Fi (4G, autre lieu), le téléphone ne peut pas joindre l\'ordinateur.',
    lien: { libelle: 'Afficher le QR code', route: '/parametres' },
  },
  {
    theme: 'Paramètres',
    question: 'Où sont mes données ? Comment les sauvegarder ?',
    reponse:
      'Les données sont sur l\'ordinateur, dans <strong>%LOCALAPPDATA%\\MediPlan</strong>. Une <strong>sauvegarde automatique</strong> est faite chaque jour ' +
      'dans <strong>Documents › MediPlan - sauvegardes</strong> (les 30 dernières sont gardées). Copiez ce dossier de temps en temps sur une clé USB ou un Drive. ' +
      '<strong>Restaurer une sauvegarde</strong> (dossier MediPlan) remet les données d\'une sauvegarde choisie.',
  },
  {
    theme: 'Paramètres',
    question: 'Comment ajouter les jours fériés ?',
    reponse:
      'Dans <strong>Paramètres › Jours fériés</strong>, cliquez sur <strong>Ajouter les fériés du Sénégal</strong> : les fêtes à date fixe et celles liées à Pâques sont calculées. ' +
      'Les fêtes musulmanes (Korité, Tabaski, Tamkharit, Maouloud, Grand Magal) dépendent de la lune : ajoutez-les quand leur date est annoncée.',
    lien: { libelle: 'Ouvrir les paramètres', route: '/parametres' },
  },
  {
    theme: 'Paramètres',
    question: 'Comment ajouter une colonne au planning (ex. la radio la nuit) ?',
    reponse:
      'Dans <strong>Services</strong>, ajoutez un <strong>sous-service</strong> puis ses <strong>plages horaires</strong> (ex. 08H-16H de jour, 16H-08H de nuit). ' +
      'Elles apparaissent aussitôt dans le planning et dans le PDF. Une plage qui finit le lendemain est reconnue automatiquement comme une garde de nuit.',
    lien: { libelle: 'Ouvrir les services', route: '/services' },
  },
  {
    theme: 'Paramètres',
    question: 'Où trouver le nombre de gardes de chaque agent ?',
    reponse:
      'Dans <strong>Rapports</strong> : gardes par agent et par mois, écart à la moyenne, gardes de week-end et de jours fériés, astreintes et absences. ' +
      'Le bouton <strong>Exporter (Excel)</strong> télécharge le tableau.',
    lien: { libelle: 'Ouvrir les rapports', route: '/rapports' },
  },
];
