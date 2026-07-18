package io.github.connellite.proxy.controller;

import io.github.connellite.proxy.model.AppSettings;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.proxy.ProxyServerManager;
import io.github.connellite.proxy.security.AdminAccountService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.ProxyUserService;
import io.github.connellite.proxy.service.SettingsService;
import io.github.connellite.proxy.service.TrafficStatsService;
import io.github.connellite.proxy.service.UserThroughput;
import io.github.connellite.proxy.dto.PasswordChangeForm;
import io.github.connellite.proxy.dto.ProxyUserForm;
import io.github.connellite.proxy.dto.SettingsForm;
#if SPRING_BOOT_3
import jakarta.validation.Valid;
#else
import javax.validation.Valid;
#endif
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ProxyUserService userService;
    private final SettingsService settingsService;
    private final ProxyServerManager proxyServerManager;
    private final ProxyMetrics proxyMetrics;
    private final TrafficStatsService trafficStatsService;
    private final AdminAccountService adminAccountService;
    private final ZoneId appZoneId;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        AppSettings settings = settingsService.get();
        List<ProxyUser> users = userService.findAll();
        model.addAttribute("settings", settings);
        model.addAttribute("httpRunning", proxyServerManager.isHttpRunning());
        model.addAttribute("httpsRunning", proxyServerManager.isHttpsRunning());
        model.addAttribute("socksRunning", proxyServerManager.isSocksRunning());
        model.addAttribute("activeConnections", proxyMetrics.getActiveConnections());
        model.addAttribute("userCount", users.size());
        model.addAttribute("enabledUsers", users.stream().filter(ProxyUser::isUsable).count());
        model.addAttribute("lastError", proxyServerManager.getLastError());
        model.addAttribute("totalBytesUp", proxyMetrics.getBytesUpTotal());
        model.addAttribute("totalBytesDown", proxyMetrics.getBytesDownTotal());
        model.addAttribute("sessionBytesUp", proxyMetrics.getBytesUpSession());
        model.addAttribute("sessionBytesDown", proxyMetrics.getBytesDownSession());
        return "dashboard";
    }

    @GetMapping("/users")
    public String users(Model model) {
        List<ProxyUser> users = userService.findAll();
        Map<Long, Integer> active = new HashMap<>();
        Map<Long, UserThroughput> speed = new HashMap<>();
        for (ProxyUser user : users) {
            active.put(user.getId(), userService.activeConnections(user.getId()));
            speed.put(user.getId(), trafficStatsService.throughputFor(user.getId()));
        }
        model.addAttribute("users", users);
        model.addAttribute("admins", adminAccountService.findAll());
        model.addAttribute("activeMap", active);
        model.addAttribute("speedMap", speed);
        return "users";
    }

    @GetMapping("/users/new")
    public String newUser(Model model) {
        model.addAttribute("form", new ProxyUserForm());
        model.addAttribute("creating", true);
        return "user-form";
    }

    @GetMapping("/users/{id}/edit")
    public String editUser(@PathVariable Long id, Model model) {
        ProxyUser user = userService.getRequired(id);
        ProxyUserForm form = new ProxyUserForm();
        form.setId(user.getId());
        form.setUsername(user.getUsername());
        form.setEnabled(user.isEnabled());
        form.setMaxConnections(user.getMaxConnections());
        if (user.getExpiresAt() != null) {
            form.setExpiresAt(DATE_FMT.format(user.getExpiresAt().atZone(appZoneId).toLocalDate()));
        }
        model.addAttribute("form", form);
        model.addAttribute("creating", false);
        return "user-form";
    }

    @PostMapping("/users")
    public String createUser(@Valid @ModelAttribute("form") ProxyUserForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("creating", true);
            return "user-form";
        }
        try {
            userService.create(form);
            redirectAttributes.addFlashAttribute("success", "User created");
            return "redirect:/users";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("creating", true);
            model.addAttribute("error", ex.getMessage());
            return "user-form";
        }
    }

    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable Long id,
                             @Valid @ModelAttribute("form") ProxyUserForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("creating", false);
            return "user-form";
        }
        try {
            userService.update(id, form);
            redirectAttributes.addFlashAttribute("success", "User updated");
            return "redirect:/users";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("creating", false);
            model.addAttribute("error", ex.getMessage());
            return "user-form";
        }
    }

    @PostMapping("/users/{id}/toggle")
    public String toggleUser(@PathVariable Long id,
                             @RequestParam boolean enabled,
                             RedirectAttributes redirectAttributes) {
        userService.setEnabled(id, enabled);
        redirectAttributes.addFlashAttribute("success", enabled ? "User enabled" : "User disabled");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/reset-traffic")
    public String resetTraffic(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.resetTraffic(id);
        redirectAttributes.addFlashAttribute("success", "Traffic counters reset");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.delete(id);
        redirectAttributes.addFlashAttribute("success", "User deleted");
        return "redirect:/users";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("form", toForm(settingsService.get()));
        model.addAttribute("httpRunning", proxyServerManager.isHttpRunning());
        model.addAttribute("httpsRunning", proxyServerManager.isHttpsRunning());
        model.addAttribute("socksRunning", proxyServerManager.isSocksRunning());
        model.addAttribute("lastError", proxyServerManager.getLastError());
        model.addAttribute("passwordForm", new PasswordChangeForm());
        return "settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@Valid @ModelAttribute("form") SettingsForm form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("passwordForm", new PasswordChangeForm());
            model.addAttribute("httpRunning", proxyServerManager.isHttpRunning());
            model.addAttribute("httpsRunning", proxyServerManager.isHttpsRunning());
            model.addAttribute("socksRunning", proxyServerManager.isSocksRunning());
            return "settings";
        }
        AppSettings settings = settingsService.get();
        applyForm(settings, form);
        settingsService.save(settings);
        proxyServerManager.restart();
        if (proxyServerManager.getLastError() != null) {
            redirectAttributes.addFlashAttribute("error", "Saved, but restart failed: " + proxyServerManager.getLastError());
        } else {
            redirectAttributes.addFlashAttribute("success", "Settings saved and proxy listeners restarted");
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/restart")
    public String restart(RedirectAttributes redirectAttributes) {
        proxyServerManager.restart();
        if (proxyServerManager.getLastError() != null) {
            redirectAttributes.addFlashAttribute("error", proxyServerManager.getLastError());
        } else {
            redirectAttributes.addFlashAttribute("success", "Proxy listeners restarted");
        }
        return "redirect:/settings";
    }

    @PostMapping("/settings/password")
    public String changePassword(@AuthenticationPrincipal UserDetails principal,
                                 @Valid @ModelAttribute("passwordForm") PasswordChangeForm passwordForm,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return settingsWithError(model, passwordForm, null);
        }
        try {
            adminAccountService.changePassword(principal.getUsername(), passwordForm);
            redirectAttributes.addFlashAttribute("success", "Admin password changed");
            return "redirect:/settings";
        } catch (IllegalArgumentException ex) {
            return settingsWithError(model, passwordForm, ex.getMessage());
        }
    }

    private String settingsWithError(Model model, PasswordChangeForm passwordForm, String error) {
        model.addAttribute("form", toForm(settingsService.get()));
        model.addAttribute("passwordForm", passwordForm);
        model.addAttribute("httpRunning", proxyServerManager.isHttpRunning());
        model.addAttribute("httpsRunning", proxyServerManager.isHttpsRunning());
        model.addAttribute("socksRunning", proxyServerManager.isSocksRunning());
        model.addAttribute("lastError", proxyServerManager.getLastError());
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "settings";
    }

    private static SettingsForm toForm(AppSettings settings) {
        SettingsForm form = new SettingsForm();
        form.setHttpEnabled(settings.isHttpEnabled());
        form.setHttpBindHost(settings.getHttpBindHost());
        form.setHttpPort(settings.getHttpPort());
        form.setHttpsEnabled(settings.isHttpsEnabled());
        form.setHttpsBindHost(settings.getHttpsBindHost());
        form.setHttpsPort(settings.getHttpsPort());
        form.setSocksEnabled(settings.isSocksEnabled());
        form.setSocksBindHost(settings.getSocksBindHost());
        form.setSocksPort(settings.getSocksPort());
        form.setAuthRequired(settings.isAuthRequired());
        return form;
    }

    private static void applyForm(AppSettings settings, SettingsForm form) {
        settings.setHttpEnabled(form.isHttpEnabled());
        settings.setHttpBindHost(form.getHttpBindHost().trim());
        settings.setHttpPort(form.getHttpPort());
        settings.setHttpsEnabled(form.isHttpsEnabled());
        settings.setHttpsBindHost(form.getHttpsBindHost().trim());
        settings.setHttpsPort(form.getHttpsPort());
        settings.setSocksEnabled(form.isSocksEnabled());
        settings.setSocksBindHost(form.getSocksBindHost().trim());
        settings.setSocksPort(form.getSocksPort());
        settings.setAuthRequired(form.isAuthRequired());
    }
}
