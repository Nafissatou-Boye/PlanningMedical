package sn.mediplan.utilisateur;

/** Profil renvoyé au frontend (jamais le mot de passe). */
public record ProfilDto(
        Long id,
        String nom,
        String prenom,
        String nomUtilisateur,
        String service,
        String poste) {

    public static ProfilDto de(Utilisateur u) {
        return new ProfilDto(
                u.getId(),
                u.getNom(),
                u.getPrenom(),
                u.getNomUtilisateur(),
                u.getService() != null ? u.getService().getNom() : null,
                u.getPoste());
    }
}
