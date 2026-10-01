package sn.mediplan.auth;

import sn.mediplan.utilisateur.ProfilDto;

/** Jeton à renvoyer dans l'en-tête « Authorization: Bearer ... » + profil de l'utilisateur. */
public record ReponseConnexion(String jeton, long expireDansSecondes, ProfilDto profil) {
}
