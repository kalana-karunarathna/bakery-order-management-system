/* Payment-only progressive enhancement. Server validation remains authoritative. */
(() => {
  "use strict";
  const cents = value => {
    const match = /^([0-9]+)(?:\.([0-9]{1,2}))?$/.exec(value.trim());
    return match ? BigInt(match[1]) * 100n + BigInt((match[2] || "").padEnd(2, "0")) : null;
  };
  // Same rules as Payment.customerReference on the server (which stays authoritative).
  const luhn = digits => {
    let sum = 0;
    for (let i = 0; i < digits.length; i++) {
      let d = Number(digits[digits.length - 1 - i]);
      if (i % 2 === 1) { d *= 2; if (d > 9) d -= 9; }
      sum += d;
    }
    return sum % 10 === 0;
  };
  const cardLike = (value, method) => {
    for (const run of value.replace(/[\s-]/g, "").match(/[0-9]{13,}/g) || [])
      if (method === "CARD" || (run.length <= 19 && luhn(run))) return true;
    return method === "CARD" && (/\b(cvv2?|cvc2?|cid|exp|expiry|expires)\b/i.test(value) ||
      /(?<![0-9])(0[1-9]|1[0-2]) ?\/ ?([0-9]{2}|20[0-9]{2})(?![0-9])/.test(value));
  };
  const CARD_MESSAGE = "This looks like card details. Never enter a card number, expiry date or CVV. Use the reference printed on the card machine slip, or leave this blank.";
  const BANK_CARD_MESSAGE = "This looks like a card number. Enter the bank transfer reference from your banking app or deposit slip instead.";
  const RESERVED_MESSAGE = "This reference format is reserved for references created by the bakery system. Enter the reference exactly as shown on your slip or banking app, or leave it blank.";
  document.querySelectorAll("form[data-payment-form]").forEach(form => {
    form.noValidate = true;
    const kind = form.dataset.paymentForm;
    const serverErrors = new Map();
    const field = name => form.elements.namedItem(name);
    const feedback = (input, message) => {
      let box = form.querySelector("#" + input.name + "-error");
      if (!box) {
        box = document.createElement("div");
        box.id = input.name + "-error";
        box.className = "invalid-feedback";
        box.setAttribute("aria-live", "polite");
        input.insertAdjacentElement("afterend", box);
      }
      box.textContent = message;
      box.classList.toggle("d-block", !!message);
      input.classList.toggle("is-invalid", !!message);
      input.setAttribute("aria-invalid", String(!!message));
      input.setAttribute("aria-describedby", box.id);
    };
    const sync = () => {
      if (kind === "create") {
        const type = field("type").value;
        const select = field("targetId");
        Array.from(select.options).forEach(option => {
          option.hidden = !!option.value && option.dataset.type !== type;
          option.disabled = option.hidden;
        });
        if (select.selectedOptions[0]?.disabled) select.value = "";
      }
      // References are optional (cash never has one); missing references are verified by an administrator.
      if (field("reference")) field("reference").required = false;
      // Admins must confirm they found the money before choosing Paid.
      if (field("verified")) field("verified").required = field("status")?.value === "PAID";
    };
    const validate = () => {
      sync();
      let valid = true;
      Array.from(form.elements).forEach(input => {
        if (!input.name || input.type === "hidden" || input.tagName === "BUTTON" || input.disabled) return;
        let message = "";
        const value = input.value.trim();
        if (input.required && (input.type === "checkbox" ? !input.checked : !value))
          message = input.name === "amount" ? "Payment amount is required." :
            input.name === "confirmed" ? "Confirm deletion first." :
            input.name === "verified" ? "Tick the verification box to confirm you found this payment before marking it Paid." :
            "Please select a valid " + input.name + ".";
        if (!message && input.tagName === "SELECT" && value &&
            !Array.from(input.options).some(o => o.value === value && !o.disabled))
          message = "Please select a valid " + input.name + ".";
        if (!message && input.name === "amount") {
          const amount = value.length <= 30 ? cents(value) : null;
          if (/^-[0-9]+(?:\.[0-9]{1,2})?$/.test(value)) message = "Payment amount must be greater than zero.";
          else if (amount === null) message = "Enter a numeric amount with a maximum of two decimal places.";
          else if (amount <= 0n) message = "Payment amount must be greater than zero.";
          else if (amount > 99999999999999n) message = "Payment amount is too large.";
          else {
            const remaining = kind === "create" ? field("targetId").selectedOptions[0]?.dataset.remaining : form.dataset.max;
            const maximum = remaining ? cents(remaining) : null;
            const purpose = field("purpose")?.value || form.dataset.purpose;
            const type = field("type")?.value || form.dataset.type;
            if (maximum !== null && amount > maximum) message = "Amount cannot exceed the remaining balance.";
            else if (purpose === "FULL" && maximum !== null && amount !== maximum)
              message = "Full payment must equal the remaining balance. Select Partial Payment for a smaller amount.";
            else if (purpose === "ADVANCE" && (type !== "BOOKING" || maximum !== null && amount >= maximum))
              message = "An advance must be less than the remaining custom cake balance.";
          }
        }
        if (!message && input.name === "reference" && value) {
          const method = field("method")?.value || form.dataset.method;
          if (method !== "CASH" && cardLike(value, method)) message = method === "CARD" ? CARD_MESSAGE : BANK_CARD_MESSAGE;
          else if (!/^[A-Za-z0-9][A-Za-z0-9 ./_-]{2,79}$/.test(value))
            message = "Use 3-80 characters: letters, numbers, spaces, dots, slashes, underscores or hyphens.";
          else if (/^(CASH|CARD|TRANSFER)-PAY[0-9]+$/i.test(value)) message = RESERVED_MESSAGE;
        }
        if (!message && input.name === "query" && (value.length > 150 || /[\x00-\x1f\x7f]/.test(value)))
          message = "Search must be at most 150 characters without control characters.";
        const server = serverErrors.get(input.name);
        if (server && input.value === server.value && !message) message = server.message;
        if (server && input.value !== server.value) {
          serverErrors.delete(input.name);
          document.querySelectorAll("[data-error-field]").forEach(item => {
            if (item.dataset.errorField === input.name) {
              const alert = item.closest("[role='alert']");
              item.remove();
              if (alert && !alert.querySelector("li")) alert.remove();
            }
          });
        }
        feedback(input, message);
        if (message) valid = false;
      });
      return valid;
    };
    sync();
    // Display server errors immediately, then remove them as the user corrects the form.
    Array.from(form.elements).forEach(input => {
      if (input.name && form.querySelector("#" + input.name + "-error")?.textContent.trim()) {
        serverErrors.set(input.name, {value: input.value, message: form.querySelector("#" + input.name + "-error").textContent.trim()});
        input.classList.add("is-invalid");
        input.setAttribute("aria-invalid", "true");
      }
    });
    form.addEventListener("input", validate);
    form.addEventListener("change", validate);
    form.addEventListener("submit", event => {
      if (!validate()) {
        event.preventDefault(); form.querySelector(".is-invalid")?.focus(); return;
      }
      if (kind === "delete" && form.dataset.modalConfirm !== "true" && !window.confirm("Permanently delete this payment record?")) event.preventDefault();
    });
  });
})();
