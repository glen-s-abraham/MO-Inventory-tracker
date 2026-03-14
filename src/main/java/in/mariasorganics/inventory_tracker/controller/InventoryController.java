package in.mariasorganics.inventory_tracker.controller;

import in.mariasorganics.inventory_tracker.service.InventoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stocks", inventoryService.getAllStock());
        model.addAttribute("requirements", inventoryService.getStockRequirements());
        model.addAttribute("pageTitle", "Inventory Dashboard");
        model.addAttribute("activePage", "inventory");
        return "inventory/dashboard";
    }

    @GetMapping("/receipt")
    public String receiptForm(Model model) {
        model.addAttribute("pageTitle", "Log Stock Receipt");
        model.addAttribute("activePage", "inventory");
        return "inventory/receipt";
    }

    @PostMapping("/receipt")
    public String logReceipt(@RequestParam String itemName, 
                             @RequestParam Double quantity, 
                             @RequestParam String unit, 
                             RedirectAttributes redirectAttributes) {
        try {
            inventoryService.logReceipt(itemName, quantity, unit);
            redirectAttributes.addFlashAttribute("success", "Stock receipt logged successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to log receipt: " + e.getMessage());
        }
        return "redirect:/inventory";
    }

    @GetMapping("/adjustment")
    public String adjustmentForm(Model model) {
        model.addAttribute("pageTitle", "Log Manual Adjustment");
        model.addAttribute("activePage", "inventory");
        return "inventory/adjustment";
    }

    @PostMapping("/adjustment")
    public String logAdjustment(@RequestParam String itemName, 
                                @RequestParam Double quantity, 
                                @RequestParam String reason, 
                                RedirectAttributes redirectAttributes) {
        try {
            inventoryService.logAdjustment(itemName, quantity, reason);
            redirectAttributes.addFlashAttribute("success", "Stock adjustment logged successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to log adjustment: " + e.getMessage());
        }
        return "redirect:/inventory";
    }
}
