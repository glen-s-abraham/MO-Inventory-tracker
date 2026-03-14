package in.mariasorganics.inventory_tracker.controller;

import in.mariasorganics.inventory_tracker.service.ConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/settings")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public String settings(Model model) {
        model.addAttribute("configs", configService.getConfigMap());
        model.addAttribute("pageTitle", "Global Configuration");
        model.addAttribute("activePage", "settings");
        return "settings";
    }

    @PostMapping("/update")
    public String updateConfigs(@RequestParam Map<String, String> allParams, RedirectAttributes redirectAttributes) {
        try {
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("_")) continue; // Skip hidden thymeleaf params if any
                
                String key = entry.getKey();
                Double value = Double.parseDouble(entry.getValue());
                
                // Special check for Dark Room Capacity warning
                if ("DARK_ROOM_CAPACITY".equals(key)) {
                   String warning = configService.checkDarkRoomCapacityWarning(value);
                   if (warning != null) {
                       redirectAttributes.addFlashAttribute("warning", warning);
                   }
                }
                
                configService.updateConfig(key, value);
            }
            redirectAttributes.addFlashAttribute("success", "Configuration updated successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update configuration: " + e.getMessage());
        }
        return "redirect:/settings";
    }
}
