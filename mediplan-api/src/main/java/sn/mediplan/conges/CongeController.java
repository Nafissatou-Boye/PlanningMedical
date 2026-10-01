package sn.mediplan.conges;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
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
import sn.mediplan.conges.CongeDtos.CongeDto;
import sn.mediplan.conges.CongeDtos.CongeRequete;

@RestController
@RequestMapping("/api/conges")
public class CongeController {

    private final CongeService conges;

    public CongeController(CongeService conges) {
        this.conges = conges;
    }

    /** Sans paramètres : tous les congés. Avec debut et fin : ceux qui touchent la période. */
    @GetMapping
    public List<CongeDto> lister(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate fin) {
        return conges.lister(debut, fin);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CongeDto creer(@Valid @RequestBody CongeRequete r) {
        return conges.creer(r);
    }

    @PutMapping("/{id}")
    public CongeDto modifier(@PathVariable Long id, @Valid @RequestBody CongeRequete r) {
        return conges.modifier(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        conges.supprimer(id);
    }
}
