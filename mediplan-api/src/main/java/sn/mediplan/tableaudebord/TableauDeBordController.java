package sn.mediplan.tableaudebord;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import sn.mediplan.tableaudebord.TableauDeBordService.Indicateurs;

@RestController
@RequestMapping("/api/tableau-de-bord")
public class TableauDeBordController {

    private final TableauDeBordService tableau;

    public TableauDeBordController(TableauDeBordService tableau) {
        this.tableau = tableau;
    }

    @GetMapping
    public Indicateurs indicateurs(@RequestParam Long serviceId) {
        return tableau.indicateurs(serviceId);
    }
}
