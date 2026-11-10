package com.bakery.user.controller;

import com.bakery.user.security.BakeryUserDetailsService;
import com.bakery.user.security.BakeryUserPrincipal;
import com.bakery.user.service.UserService;
import com.bakery.user.service.UserValidationException;
import com.bakery.user.service.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The signed-in user's own account. No route takes a user ID: the account is always the one in
 * the authenticated session, so nobody can reach another customer's profile by editing a URL.
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;
    private final BakeryUserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public ProfileController(UserService userService, BakeryUserDetailsService userDetailsService) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
    }

    @InitBinder("form")
    void allowProfileFields(WebDataBinder binder) {
        binder.setAllowedFields(ProfileForm.OWN_FIELDS);
    }

    @InitBinder("passwordForm")
    void allowPasswordFields(WebDataBinder binder) {
        binder.setAllowedFields(PasswordForm.FIELDS);
    }

    @GetMapping
    public String profile(@AuthenticationPrincipal BakeryUserPrincipal me, Model model) {
        model.addAttribute("user", userService.getView(me.getId()));
        return "user/profile";
    }

    // ── Edit details ──────────────────────────────────────────────────────────

    @GetMapping("/edit")
    public String editForm(@AuthenticationPrincipal BakeryUserPrincipal me, Model model) {
        UserView user = userService.getView(me.getId());
        model.addAttribute("user", user);
        model.addAttribute("form", ProfileForm.from(user));
        return "user/profile-edit";
    }

    @PostMapping("/edit")
    public String edit(@AuthenticationPrincipal BakeryUserPrincipal me,
                       @ModelAttribute("form") ProfileForm form, BindingResult result, Model model,
                       HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        UserView updated;
        try {
            updated = userService.updateOwnProfile(me.getId(), form.getFullName(), form.getEmail(),
                    form.getPhone(), form.getAddress());
        } catch (UserValidationException e) {
            FormErrors.apply(e, result);
            model.addAttribute("user", userService.getView(me.getId()));
            return "user/profile-edit";
        }
        refreshSignedInUser(updated.email(), request, response);
        redirect.addFlashAttribute("successMessage", "Your details have been updated.");
        return "redirect:/profile";
    }

    // ── Password ──────────────────────────────────────────────────────────────

    @GetMapping("/password")
    public String passwordForm(Model model) {
        model.addAttribute("passwordForm", new PasswordForm());
        return "user/password";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal BakeryUserPrincipal me,
                                 @ModelAttribute("passwordForm") PasswordForm form, BindingResult result,
                                 RedirectAttributes redirect) {
        try {
            userService.changePassword(me.getId(), form.getCurrentPassword(), form.getNewPassword(),
                    form.getConfirmPassword());
        } catch (UserValidationException e) {
            FormErrors.apply(e, result);
            form.clear();
            return "user/password";
        }
        redirect.addFlashAttribute("successMessage", "Your password has been changed.");
        return "redirect:/profile";
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @GetMapping("/delete")
    public String deleteConfirm(@AuthenticationPrincipal BakeryUserPrincipal me, Model model) {
        model.addAttribute("user", userService.getView(me.getId()));
        return "user/delete";
    }

    @PostMapping("/delete")
    public String delete(@AuthenticationPrincipal BakeryUserPrincipal me,
                         @RequestParam(defaultValue = "") String password, Model model,
                         HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        try {
            userService.deleteOwnAccount(me.getId(), password);
        } catch (UserValidationException e) {
            model.addAttribute("user", userService.getView(me.getId()));
            model.addAttribute("passwordError", e.getFieldErrors().get("password"));
            return "user/delete";
        } catch (IllegalStateException e) {
            model.addAttribute("user", userService.getView(me.getId()));
            model.addAttribute("errorMessage", e.getMessage());
            return "user/delete";
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, auth);
        redirect.addFlashAttribute("successMessage", "Your account has been deleted.");
        return "redirect:/login?deleted";
    }

    /** Re-issues the session's authentication so it carries the user's new email and name. */
    private void refreshSignedInUser(String email, HttpServletRequest request, HttpServletResponse response) {
        BakeryUserPrincipal fresh = (BakeryUserPrincipal) userDetailsService.loadUserByUsername(email);
        fresh.eraseCredentials();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
