package sn.mediplan.personnel;

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
import sn.mediplan.personnel.PersonnelDtos.AgentDto;
import sn.mediplan.personnel.PersonnelDtos.AgentRequete;
import sn.mediplan.personnel.PersonnelDtos.ProfessionRequete;
import sn.mediplan.personnel.PersonnelDtos.ReferenceDto;

@RestController
@RequestMapping("/api")
public class PersonnelController {

    private final PersonnelService personnel;

    public PersonnelController(PersonnelService personnel) {
        this.personnel = personnel;
    }

    @GetMapping("/agents")
    public List<AgentDto> lister() {
        return personnel.lister();
    }

    @GetMapping("/agents/{id}")
    public AgentDto lire(@PathVariable Long id) {
        return personnel.lire(id);
    }

    @PostMapping("/agents")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentDto creer(@Valid @RequestBody AgentRequete r) {
        return personnel.lire(personnel.creer(r));
    }

    @PutMapping("/agents/{id}")
    public AgentDto modifier(@PathVariable Long id, @Valid @RequestBody AgentRequete r) {
        personnel.modifier(id, r);
        return personnel.lire(id);
    }

    @GetMapping("/professions")
    public List<ReferenceDto> professions() {
        return personnel.professions();
    }

    @PostMapping("/professions")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ReferenceDto> creerProfession(@Valid @RequestBody ProfessionRequete r) {
        personnel.creerProfession(r);
        return personnel.professions();
    }
}
