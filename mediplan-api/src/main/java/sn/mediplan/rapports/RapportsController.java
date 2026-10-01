package sn.mediplan.rapports;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sn.mediplan.rapports.RapportsService.Rapport;

@RestController
@RequestMapping("/api/rapports")
public class RapportsController {

    private final RapportsService rapports;

    public RapportsController(RapportsService rapports) {
        this.rapports = rapports;
    }

    @GetMapping
    public Rapport rapport(@RequestParam Long serviceId, @RequestParam int annee) {
        return rapports.rapport(serviceId, annee);
    }
}
