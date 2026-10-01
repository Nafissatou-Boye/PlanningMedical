package sn.mediplan.planning;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import sn.mediplan.planning.JoursFeriesService.JourFerieDto;
import sn.mediplan.planning.JoursFeriesService.JourFerieRequete;

@RestController
@RequestMapping("/api/jours-feries")
public class JoursFeriesController {

    private final JoursFeriesService feries;

    public JoursFeriesController(JoursFeriesService feries) {
        this.feries = feries;
    }

    @GetMapping
    public List<JourFerieDto> lister(@RequestParam int annee) {
        return feries.lister(annee);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JourFerieDto creer(@Valid @RequestBody JourFerieRequete r) {
        return feries.creer(r);
    }

    @PutMapping("/{id}")
    public JourFerieDto modifier(@PathVariable Long id, @Valid @RequestBody JourFerieRequete r) {
        return feries.modifier(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        feries.supprimer(id);
    }

    /** Ajoute en un clic les fériés calculables de l'année (dates fixes et fêtes liées à Pâques). */
    @PostMapping("/senegal/{annee}")
    public List<JourFerieDto> ajouterFeriesSenegal(@PathVariable int annee) {
        return feries.ajouterFeriesSenegal(annee);
    }
}
