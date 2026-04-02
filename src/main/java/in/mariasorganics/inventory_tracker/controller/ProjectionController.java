package in.mariasorganics.inventory_tracker.controller;

import in.mariasorganics.inventory_tracker.service.ConfigService;
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
    private final ConfigService configService;

    public ProjectionController(ProjectionService projectionService, ConfigService configService) {
        this.projectionService = projectionService;
        this.configService = configService;
    }

    @GetMapping("/simulation")
    public String showSimulationForm(Model model) {
        LocalDate orderDate = LocalDate.now();
        int suggestedBags = projectionService.getSuggestedPlannedBags(orderDate);
        Map<String, Double> configs = configService.getConfigMap();
        Double leadTimeDays = configs.getOrDefault("SUPPLIER_LEAD_TIME_DAYS", 15.0);
        
        // Default to today
        model.addAttribute("orderDate", orderDate);
        model.addAttribute("plannedBags", suggestedBags);
        model.addAttribute("projectionDays", 25);
        model.addAttribute("overrideLeadTime", leadTimeDays);
        return "projection/simulation";
    }

    @PostMapping("/simulate")
    public String simulate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDate,
                           @RequestParam(defaultValue = "0") Integer plannedBags,
                           @RequestParam(required = false) Integer projectionDays,
                           @RequestParam(required = false) Double overrideLeadTime,
                           Model model) {
        Map<String, Object> results = projectionService.calculateProjection(orderDate, plannedBags, projectionDays, overrideLeadTime);
        model.addAllAttributes(results);
        model.addAttribute("simulated", true);
        model.addAttribute("overrideLeadTime", overrideLeadTime);
        return "projection/simulation";
    }
}
