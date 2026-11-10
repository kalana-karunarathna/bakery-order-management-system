package com.bakery.user.controller;

import com.bakery.user.security.LoginFailureHandler;
import com.bakery.user.security.RoleBasedLoginSuccessHandler;
import com.bakery.user.service.UserService;
import com.bakery.user.service.UserValidationException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Sign-in page, customer registration and the access-denied page.
 * The sign-in POST and sign-out POST themselves are handled by Spring Security (see SecurityConfig).
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @InitBinder("form")
    void allowOnlyRegistrationFields(WebDataBinder binder) {
        binder.setAllowedFields(RegistrationForm.FIELDS);
    }

    @GetMapping("/login")
    public String login(Authentication authentication, HttpSession session, Model model) {
        if (isSignedIn(authentication)) {
            return "redirect:" + RoleBasedLoginSuccessHandler.homeFor(authentication);
        }
        Object lastEmail = session.getAttribute(LoginFailureHandler.LAST_EMAIL_ATTRIBUTE);
        session.removeAttribute(LoginFailureHandler.LAST_EMAIL_ATTRIBUTE);
        model.addAttribute("lastEmail", lastEmail == null ? "" : lastEmail);
        return "user/login";
    }

    @GetMapping("/register")
    public String registerForm(Authentication authentication, Model model) {
        if (isSignedIn(authentication)) {
            return "redirect:" + RoleBasedLoginSuccessHandler.homeFor(authentication);
        }
        model.addAttribute("form", new RegistrationForm());
        return "user/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("form") RegistrationForm form, BindingResult result,
                           Authentication authentication, RedirectAttributes redirect) {
        if (isSignedIn(authentication)) {
            return "redirect:" + RoleBasedLoginSuccessHandler.homeFor(authentication);
        }
        try {
            userService.register(form.getFullName(), form.getEmail(), form.getPhone(), form.getAddress(),
                    form.getPassword(), form.getConfirmPassword());
        } catch (UserValidationException e) {
            FormErrors.apply(e, result);
            form.clearPasswords();
            return "user/register";
        }
        redirect.addFlashAttribute("successMessage", "Your account is ready. Please sign in.");
        return "redirect:/login?registered";
    }

    @GetMapping("/access-denied")
    public String accessDenied(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return "user/access-denied";
    }

    private static boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
