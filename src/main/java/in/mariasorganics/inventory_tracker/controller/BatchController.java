package in.mariasorganics.inventory_tracker.controller;

import in.mariasorganics.inventory_tracker.model.Batch;
import in.mariasorganics.inventory_tracker.service.BatchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/production")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("batches", batchService.getAllBatches());
        model.addAttribute("activeBatches", batchService.getActiveBatches());
        model.addAttribute("today", LocalDate.now());
        
        // Add capacity for the progress bar
        model.addAttribute("capacity", 900.0); // Default or fetch from config
        return "production/index";
    }

    @PostMapping("/log")
    public String logProduction(@RequestParam("inoculationDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inoculationDate,
                                @RequestParam("bagCount") Integer bagCount,
                                RedirectAttributes redirectAttributes) {
        try {
            Batch batch = batchService.createBatch(inoculationDate, bagCount);
            redirectAttributes.addFlashAttribute("successMessage", "Batch " + batch.getBatchId() + " logged successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error logging production: " + e.getMessage());
        }
        return "redirect:/production";
    }
}
