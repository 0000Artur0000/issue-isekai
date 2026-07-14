package ru.arzer0.issueisekai.panel.admin;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.arzer0.issueisekai.panel.server.ServerService;
import ru.arzer0.issueisekai.panel.user.UserAccount;
import ru.arzer0.issueisekai.panel.user.UserService;

@Controller
public class AdminController {
    private final UserService users;
    private final ServerService servers;

    public AdminController(UserService users, ServerService servers) {
        this.users = users;
        this.servers = servers;
    }

    @GetMapping("/users")
    String users(Model model) {
        model.addAttribute("users", users.list());
        model.addAttribute("roles", UserAccount.Role.values());
        return "users";
    }

    @PostMapping("/users")
    String createUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam UserAccount.Role role,
            RedirectAttributes redirect) {
        users.create(username, password, role);
        redirect.addFlashAttribute("message", "User created");
        return "redirect:/users";
    }

    @PostMapping("/users/{id}")
    String updateUser(
            @PathVariable UUID id,
            @RequestParam UserAccount.Role role,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam(defaultValue = "") String password,
            RedirectAttributes redirect) {
        users.update(id, role, enabled, password);
        redirect.addFlashAttribute("message", "User updated");
        return "redirect:/users";
    }

    @GetMapping("/servers")
    String servers(Model model) {
        model.addAttribute("servers", servers.list());
        return "servers";
    }

    @PostMapping("/servers")
    String createServer(@RequestParam String name, RedirectAttributes redirect) {
        ServerService.Credentials credentials = servers.create(name);
        redirect.addFlashAttribute("message", "Server created");
        redirect.addFlashAttribute("apiKey", credentials.apiKey());
        return "redirect:/servers";
    }

    @PostMapping("/servers/{id}/rotate")
    String rotateServerKey(@PathVariable UUID id, RedirectAttributes redirect) {
        redirect.addFlashAttribute("message", "API key rotated");
        redirect.addFlashAttribute("apiKey", servers.rotateKey(id));
        return "redirect:/servers";
    }

    @PostMapping("/servers/{id}/disable")
    String disableServer(@PathVariable UUID id, RedirectAttributes redirect) {
        servers.disable(id);
        redirect.addFlashAttribute("message", "Server disabled");
        return "redirect:/servers";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
