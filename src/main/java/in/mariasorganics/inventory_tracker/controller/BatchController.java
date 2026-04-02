package in.mariasorganics.inventory_tracker.controller;

import in.mariasorganics.inventory_tracker.model.Batch;
import in.mariasorganics.inventory_tracker.service.BatchService;
import in.mariasorganics.inventory_tracker.service.ConfigService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequestMapping("/production")
public class BatchController {

    private final BatchService batchService;
    private final ConfigService configService;

    public BatchController(BatchService batchService, ConfigService configService) {
        this.batchService = batchService;
        this.configService = configService;
    }

    @GetMapping
    public String index(Model model,
                        @RequestParam(value = "activePage", defaultValue = "0") int activePage,
                        @RequestParam(value = "completedPage", defaultValue = "0") int completedPage,
                        @RequestParam(value = "activeTab", defaultValue = "active") String activeTab) {
        
        batchService.activatePlannedBatches();
        int pageSize = 10;
        Page<Batch> activeBatches = batchService.getActiveBatchesPaginated(activePage, pageSize);
        Page<Batch> completedBatches = batchService.getCompletedBatchesPaginated(completedPage, pageSize);

        model.addAttribute("activeBatches", activeBatches.getContent());
        model.addAttribute("activePage", activePage);
        model.addAttribute("activeTotalPages", activeBatches.getTotalPages());
        
        model.addAttribute("completedBatches", completedBatches.getContent());
        model.addAttribute("completedPage", completedPage);
        model.addAttribute("completedTotalPages", completedBatches.getTotalPages());
        
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("today", LocalDate.now());
        
        // Add capacity for the progress bar from config
        Map<String, Double> configMap = configService.getConfigMap();
        model.addAttribute("capacity", configMap.getOrDefault("DARK_ROOM_CAPACITY", 900.0));
        
        // Total active bags for occupancy summary
        model.addAttribute("totalActiveBags", batchService.getTotalActiveBags());
        model.addAttribute("totalBatches", activeBatches.getTotalElements() + completedBatches.getTotalElements());
        
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

    @PostMapping("/checkout")
    public String checkoutBags(@RequestParam("totalToCheckout") Integer totalToCheckout,
                               RedirectAttributes redirectAttributes) {
        try {
            batchService.checkoutBags(totalToCheckout);
            redirectAttributes.addFlashAttribute("successMessage", totalToCheckout + " bags moved out successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error during checkout: " + e.getMessage());
        }
        return "redirect:/production";
    }

    @PostMapping("/update-count")
    public String updateBatchCount(@RequestParam("id") Long id,
                                   @RequestParam("newCount") Integer newCount,
                                   RedirectAttributes redirectAttributes) {
        try {
            batchService.updateBatchCount(id, newCount);
            redirectAttributes.addFlashAttribute("successMessage", "Batch count updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating batch: " + e.getMessage());
        }
        return "redirect:/production";
    }

    @PostMapping("/revert/{id}")
    public String revertBatch(@PathVariable("id") Long id,
                             RedirectAttributes redirectAttributes) {
        try {
            batchService.revertToActive(id);
            redirectAttributes.addFlashAttribute("successMessage", "Batch reverted to ACTIVE successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error reverting batch: " + e.getMessage());
        }
        return "redirect:/production";
    }

    @PostMapping("/delete/{id}")
    public String deleteBatch(@PathVariable("id") Long id,
                             RedirectAttributes redirectAttributes) {
        try {
            batchService.deleteBatch(id);
            redirectAttributes.addFlashAttribute("successMessage", "Batch deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting batch: " + e.getMessage());
        }
        return "redirect:/production";
    }
}
