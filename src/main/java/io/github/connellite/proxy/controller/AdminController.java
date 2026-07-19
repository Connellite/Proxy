package io.github.connellite.proxy.controller;

import io.github.connellite.proxy.model.AppSettings;
import io.github.connellite.proxy.model.ProxyUser;
import io.github.connellite.proxy.proxy.ProxyServerManager;
import io.github.connellite.proxy.proxy.ProxyTlsService;
import io.github.connellite.proxy.security.AdminAccountService;
import io.github.connellite.proxy.service.ProxyMetrics;
import io.github.connellite.proxy.service.ProxyUserService;
import io.github.connellite.proxy.service.SettingsService;
import io.github.connellite.proxy.service.TrafficStatsService;
import io.github.connellite.proxy.service.UserThroughput;
import io.github.connellite.proxy.dto.EncryptionForm;
import io.github.connellite.proxy.dto.PasswordChangeForm;
import io.github.connellite.proxy.dto.ProxyUserForm;
import io.github.connellite.proxy.dto.SettingsForm;
import io.github.connellite.proxy.dto.TlsStatus;
#if SPRING_BOOT_3
import jakarta.validation.Valid;
#else
import javax.validation.Valid;
#endif
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
    private final ProxyTlsService tlsService;
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
    public String editUser(@PathVariable("id") Long id, Model model) {
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
    public String updateUser(@PathVariable("id") Long id,
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
    public String toggleUser(@PathVariable("id") Long id,
                             @RequestParam("enabled") boolean enabled,
                             RedirectAttributes redirectAttributes) {
        userService.setEnabled(id, enabled);
        redirectAttributes.addFlashAttribute("success", enabled ? "User enabled" : "User disabled");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/reset-traffic")
    public String resetTraffic(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        userService.resetTraffic(id);
        redirectAttributes.addFlashAttribute("success", "Traffic counters reset");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        userService.delete(id);
        redirectAttributes.addFlashAttribute("success", "User deleted");
        return "redirect:/users";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        return renderSettings(model, toSettingsForm(settingsService.get()), new PasswordChangeForm(), null);
    }

    @PostMapping("/settings")
    public String saveSettings(@Valid @ModelAttribute("form") SettingsForm form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderSettings(model, form, new PasswordChangeForm(), null);
        }
        AppSettings settings = settingsService.get();
        applySettingsForm(settings, form);
        settingsService.save(settings);
        proxyServerManager.restart();
        if (proxyServerManager.getLastError() != null) {
            redirectAttributes.addFlashAttribute("error",
                    "Saved, but restart failed: " + proxyServerManager.getLastError());
        } else {
            redirectAttributes.addFlashAttribute("success", "Settings saved and proxy listeners restarted");
        }
        return "redirect:/settings";
    }

    @GetMapping("/settings/encryption")
    public String encryption(Model model) {
        return renderEncryption(model, toEncryptionForm(settingsService.get()), null);
    }

    @PostMapping("/settings/encryption")
    public String saveEncryption(@Valid @ModelAttribute("form") EncryptionForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderEncryption(model, form, null);
        }
        try {
            AppSettings settings = settingsService.get();
            applyEncryptionForm(settings, form);
            tlsService.validateSettingsOrThrow(settings);
            settingsService.save(settings);
            proxyServerManager.restart();
            if (proxyServerManager.getLastError() != null) {
                redirectAttributes.addFlashAttribute("error",
                        "Saved, but restart failed: " + proxyServerManager.getLastError());
            } else {
                redirectAttributes.addFlashAttribute("success", "Encryption settings saved and listeners restarted");
            }
            return "redirect:/settings/encryption";
        } catch (IllegalArgumentException ex) {
            return renderEncryption(model, form, ex.getMessage());
        }
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
            return renderSettings(model, toSettingsForm(settingsService.get()), passwordForm, null);
        }
        try {
            adminAccountService.changePassword(principal.getUsername(), passwordForm);
            redirectAttributes.addFlashAttribute("success", "Admin password changed");
            return "redirect:/settings";
        } catch (IllegalArgumentException ex) {
            return renderSettings(model, toSettingsForm(settingsService.get()), passwordForm, ex.getMessage());
        }
    }

    private String renderSettings(Model model, SettingsForm form, PasswordChangeForm passwordForm, String error) {
        model.addAttribute("form", form);
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

    private String renderEncryption(Model model, EncryptionForm form, String error) {
        AppSettings settings = settingsService.get();
        TlsStatus tlsStatus;
        try {
            AppSettings preview = copySettings(settings);
            applyEncryptionForm(preview, form);
            tlsStatus = tlsService.status(preview);
        } catch (IllegalArgumentException ex) {
            tlsStatus = tlsService.status(settings);
            if (error == null) {
                error = ex.getMessage();
            }
        }

        model.addAttribute("form", form);
        model.addAttribute("httpsRunning", proxyServerManager.isHttpsRunning());
        model.addAttribute("lastError", proxyServerManager.getLastError());
        model.addAttribute("tlsStatus", tlsStatus);
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "encryption";
    }

    private static SettingsForm toSettingsForm(AppSettings settings) {
        SettingsForm form = new SettingsForm();
        form.setHttpEnabled(settings.isHttpEnabled());
        form.setHttpBindHost(settings.getHttpBindHost());
        form.setHttpPort(settings.getHttpPort());
        form.setSocksEnabled(settings.isSocksEnabled());
        form.setSocksBindHost(settings.getSocksBindHost());
        form.setSocksPort(settings.getSocksPort());
        form.setHttpAuthRequired(settings.isHttpAuthRequired());
        form.setSocksAuthRequired(settings.isSocksAuthRequired());
        form.setSocksUdpEnabled(settings.isSocksUdpEnabled());
        return form;
    }

    private static EncryptionForm toEncryptionForm(AppSettings settings) {
        EncryptionForm form = new EncryptionForm();
        form.setHttpsEnabled(settings.isHttpsEnabled());
        form.setHttpsBindHost(settings.getHttpsBindHost());
        form.setHttpsPort(settings.getHttpsPort());
        form.setServerName(settings.getHttpsServerName() != null ? settings.getHttpsServerName() : "");
        form.setCertificateChain(settings.getHttpsCertificateChain());
        form.setCertificatePath(settings.getHttpsCertificatePath());
        form.setPrivateKey(null);
        form.setPrivateKeyPath(settings.getHttpsPrivateKeyPath());
        form.setPrivateKeySaved(StringUtils.isNotBlank(settings.getHttpsPrivateKey()));
        return form;
    }

    private static void applySettingsForm(AppSettings settings, SettingsForm form) {
        settings.setHttpEnabled(form.isHttpEnabled());
        settings.setHttpBindHost(form.getHttpBindHost().trim());
        settings.setHttpPort(form.getHttpPort());
        settings.setSocksEnabled(form.isSocksEnabled());
        settings.setSocksBindHost(form.getSocksBindHost().trim());
        settings.setSocksPort(form.getSocksPort());
        settings.setHttpAuthRequired(form.isHttpAuthRequired());
        settings.setSocksAuthRequired(form.isSocksAuthRequired());
        settings.setSocksUdpEnabled(form.isSocksUdpEnabled());
    }

    private static void applyEncryptionForm(AppSettings settings, EncryptionForm form) {
        settings.setHttpsEnabled(form.isHttpsEnabled());
        settings.setHttpsBindHost(form.getHttpsBindHost().trim());
        settings.setHttpsPort(form.getHttpsPort());
        settings.setHttpsServerName(StringUtils.trimToNull(form.getServerName()));
        applyCertificateFields(settings, form);
        applyPrivateKeyFields(settings, form);
    }

    private static void applyCertificateFields(AppSettings settings, EncryptionForm form) {
        String chain = StringUtils.trimToNull(form.getCertificateChain());
        String path = StringUtils.trimToNull(form.getCertificatePath());
        if (chain != null && path != null) {
            throw new IllegalArgumentException("certificate data and file can't be set together");
        }
        if (path != null) {
            settings.setHttpsCertificatePath(path);
            settings.setHttpsCertificateChain(null);
        } else {
            settings.setHttpsCertificatePath(null);
            settings.setHttpsCertificateChain(chain);
        }
    }

    private static void applyPrivateKeyFields(AppSettings settings, EncryptionForm form) {
        String key = form.getPrivateKey();
        boolean keyProvided = StringUtils.isNotBlank(key);
        String path = StringUtils.trimToNull(form.getPrivateKeyPath());
        if (keyProvided && path != null) {
            throw new IllegalArgumentException("private key data and file can't be set together");
        }
        if (path != null) {
            settings.setHttpsPrivateKeyPath(path);
            settings.setHttpsPrivateKey(null);
        } else if (keyProvided) {
            settings.setHttpsPrivateKey(key.trim());
            settings.setHttpsPrivateKeyPath(null);
        } else if (form.isPrivateKeySaved()) {
            settings.setHttpsPrivateKeyPath(null);
        } else {
            settings.setHttpsPrivateKey(null);
            settings.setHttpsPrivateKeyPath(null);
        }
    }

    private static AppSettings copySettings(AppSettings source) {
        AppSettings copy = new AppSettings();
        copy.setId(source.getId());
        copy.setHttpEnabled(source.isHttpEnabled());
        copy.setHttpBindHost(source.getHttpBindHost());
        copy.setHttpPort(source.getHttpPort());
        copy.setHttpsEnabled(source.isHttpsEnabled());
        copy.setHttpsBindHost(source.getHttpsBindHost());
        copy.setHttpsPort(source.getHttpsPort());
        copy.setHttpsServerName(source.getHttpsServerName());
        copy.setHttpsCertificateChain(source.getHttpsCertificateChain());
        copy.setHttpsCertificatePath(source.getHttpsCertificatePath());
        copy.setHttpsPrivateKey(source.getHttpsPrivateKey());
        copy.setHttpsPrivateKeyPath(source.getHttpsPrivateKeyPath());
        copy.setSocksEnabled(source.isSocksEnabled());
        copy.setSocksBindHost(source.getSocksBindHost());
        copy.setSocksPort(source.getSocksPort());
        copy.setHttpAuthRequired(source.isHttpAuthRequired());
        copy.setSocksAuthRequired(source.isSocksAuthRequired());
        copy.setSocksUdpEnabled(source.isSocksUdpEnabled());
        copy.setBytesUpTotal(source.getBytesUpTotal());
        copy.setBytesDownTotal(source.getBytesDownTotal());
        return copy;
    }
}
