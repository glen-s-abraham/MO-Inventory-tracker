package in.mariasorganics.inventory_tracker.controller;

import in.mariasorganics.inventory_tracker.service.ProjectionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/projection")
public class ProjectionController {

    private final ProjectionService projectionService;

    public ProjectionController(ProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping("/simulation")
    public String showSimulationForm(Model model) {
        LocalDate orderDate = LocalDate.now();
        int suggestedBags = projectionService.getSuggestedPlannedBags(orderDate);
        
        // Default to today
        model.addAttribute("orderDate", orderDate);
        model.addAttribute("plannedBags", suggestedBags);
        model.addAttribute("projectionDays", 25);
        return "projection/simulation";
    }

    @PostMapping("/simulate")
    public String simulate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDate,
                           @RequestParam(defaultValue = "0") Integer plannedBags,
                           @RequestParam(required = false) Integer projectionDays,
                           Model model) {
        Map<String, Object> results = projectionService.calculateProjection(orderDate, plannedBags, projectionDays);
        model.addAllAttributes(results);
        model.addAttribute("simulated", true);
        return "projection/simulation";
    }
}
