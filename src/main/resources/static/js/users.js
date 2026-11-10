/*
 * User Management enhancements. Optional: every form works without JavaScript and the
 * server validates everything again. The password checklist is a hint, not a rule check.
 */
(() => {
    "use strict";

    /* Show / hide password buttons. */
    document.querySelectorAll("[data-toggle-password]").forEach((button) => {
        const input = document.getElementById(button.dataset.togglePassword);
        if (!input) return;
        button.hidden = false;
        button.addEventListener("click", () => {
            const show = input.type === "password";
            input.type = show ? "text" : "password";
            button.setAttribute("aria-pressed", String(show));
            button.setAttribute("aria-label", show ? "Hide password" : "Show password");
        });
    });

    /* Password checklist (mirrors UserInputRules: 8+ chars, a letter, a number). */
    document.querySelectorAll("[data-password-meter]").forEach((meter) => {
        const input = document.getElementById(meter.dataset.passwordMeter);
        const rules = document.querySelectorAll(`[data-rule-for="${meter.dataset.passwordMeter}"]`);
        if (!input) return;
        const checks = {
            length: (v) => v.length >= 8 && v.length <= 64,
            letter: (v) => /\p{L}/u.test(v),
            number: (v) => /\d/.test(v),
        };
        const update = () => {
            const value = input.value;
            let met = 0;
            rules.forEach((li) => {
                const ok = checks[li.dataset.rule](value);
                li.classList.toggle("is-met", ok);
                if (ok) met++;
            });
            meter.dataset.level = value ? String(met) : "0";
        };
        input.addEventListener("input", update);
        update();
    });

    /* Accessible confirmation for destructive forms (data-confirm). */
    const dialog = document.getElementById("userConfirmDialog");
    let pending = null;
    document.querySelectorAll("form[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (form.dataset.confirmed === "true") return;
            event.preventDefault();
            if (!dialog || typeof dialog.showModal !== "function") {
                if (window.confirm(form.dataset.confirm)) {
                    form.dataset.confirmed = "true";
                    form.submit();
                }
                return;
            }
            pending = form;
            dialog.querySelector("#userConfirmTitle").textContent = form.dataset.confirmTitle || "Are you sure?";
            dialog.querySelector("#userConfirmMessage").textContent = form.dataset.confirm;
            dialog.querySelector("[data-dialog-confirm]").textContent = form.dataset.confirmAction || "Confirm";
            dialog.showModal();
            dialog.querySelector("[data-dialog-cancel]").focus();
        });
    });
    if (dialog) {
        dialog.querySelector("[data-dialog-cancel]").addEventListener("click", () => dialog.close("cancel"));
        dialog.querySelector("[data-dialog-confirm]").addEventListener("click", () => dialog.close("confirm"));
        dialog.addEventListener("click", (e) => { if (e.target === dialog) dialog.close("cancel"); });
        dialog.addEventListener("close", () => {
            const form = pending;
            pending = null;
            if (form && dialog.returnValue === "confirm") {
                form.dataset.confirmed = "true";
                form.submit();
            } else if (form) {
                const trigger = form.querySelector('button[type="submit"]');
                if (trigger) trigger.focus();
            }
        });
    }
})();
