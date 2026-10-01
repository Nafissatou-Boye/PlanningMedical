package sn.mediplan.parametrage;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sn.mediplan.parametrage.ParametrageDtos.PlageRequete;
import sn.mediplan.parametrage.ParametrageDtos.PosteStageRequete;
import sn.mediplan.parametrage.ParametrageDtos.ServiceDto;
import sn.mediplan.parametrage.ParametrageDtos.ServiceRequete;
import sn.mediplan.parametrage.ParametrageDtos.SousServiceRequete;

/**
 * Chaque écriture renvoie l'arborescence à jour : le frontend se rafraîchit en un seul appel.
 */
@RestController
@RequestMapping("/api")
public class ParametrageController {

    private final ParametrageService parametrage;

    public ParametrageController(ParametrageService parametrage) {
        this.parametrage = parametrage;
    }

    @GetMapping("/services")
    public List<ServiceDto> lister() {
        return parametrage.lister();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ServiceDto> creerService(@Valid @RequestBody ServiceRequete r) {
        parametrage.creerService(r);
        return parametrage.lister();
    }

    @PutMapping("/services/{id}")
    public List<ServiceDto> modifierService(@PathVariable Long id, @Valid @RequestBody ServiceRequete r) {
        parametrage.modifierService(id, r);
        return parametrage.lister();
    }

    @PostMapping("/services/{id}/sous-services")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ServiceDto> creerSousService(@PathVariable Long id, @Valid @RequestBody SousServiceRequete r) {
        parametrage.creerSousService(id, r);
        return parametrage.lister();
    }

    @PutMapping("/sous-services/{id}")
    public List<ServiceDto> modifierSousService(@PathVariable Long id, @Valid @RequestBody SousServiceRequete r) {
        parametrage.modifierSousService(id, r);
        return parametrage.lister();
    }

    @PostMapping("/sous-services/{id}/plages")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ServiceDto> creerPlage(@PathVariable Long id, @Valid @RequestBody PlageRequete r) {
        parametrage.creerPlage(id, r);
        return parametrage.lister();
    }

    @PutMapping("/plages/{id}")
    public List<ServiceDto> modifierPlage(@PathVariable Long id, @Valid @RequestBody PlageRequete r) {
        parametrage.modifierPlage(id, r);
        return parametrage.lister();
    }

    @PostMapping("/services/{id}/postes-stage")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ServiceDto> creerPoste(@PathVariable Long id, @Valid @RequestBody PosteStageRequete r) {
        parametrage.creerPoste(id, r);
        return parametrage.lister();
    }

    @PutMapping("/postes-stage/{id}")
    public List<ServiceDto> modifierPoste(@PathVariable Long id, @Valid @RequestBody PosteStageRequete r) {
        parametrage.modifierPoste(id, r);
        return parametrage.lister();
    }
}
