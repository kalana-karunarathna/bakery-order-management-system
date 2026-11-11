/* Presentation enhancements only. Payment validation and POST routes stay authoritative. */
(() => {
  "use strict";
  const create = document.querySelector('form[data-payment-form="create"]');
  const cents = value => {
    const match = /^([0-9]{1,12})(?:\.([0-9]{1,2}))?$/.exec((value || "").trim());
    return match ? BigInt(match[1]) * 100n + BigInt((match[2] || "").padEnd(2,"0")) : null;
  };
  const currency = value => {
    if (value === null) return "—";
    const sign = value < 0n ? "-" : "";
    const absolute = value < 0n ? -value : value;
    return "LKR " + sign + (absolute / 100n).toLocaleString("en-LK") + "." + (absolute % 100n).toString().padStart(2,"0");
  };
  if (create) {
    const field = name => create.elements.namedItem(name);
    const show = (name, value) => { const node = document.querySelector("[data-summary-" + name + "]"); if (node) node.textContent = value; };
    const update = () => {
      const option = field("targetId").selectedOptions[0];
      const selected = !!option?.value && !option.disabled;
      const data = selected ? option.dataset : {};
      const amount = cents(field("amount").value);
      const remaining = cents(data.remaining);
      show("customer",data.customer || "Not selected");
      show("target",selected ? option.value : "Not selected");
      ["total","paid","pending","remaining"].forEach(name => show(name,currency(cents(data[name]))));
      show("amount",currency(amount || 0n));
      show("after",remaining !== null && amount !== null
        ? amount <= remaining ? "Available after this payment: " + currency(remaining - amount)
          : "This amount exceeds the available balance."
        : "Choose an order and enter an amount.");
      const placeholder = document.querySelector("[data-summary-placeholder]");
      if (placeholder) placeholder.hidden = selected;
      const method = field("method").value;
      create.querySelectorAll("[data-method-choice]").forEach(button =>
        button.setAttribute("aria-pressed",String(button.dataset.methodChoice === method)));
      // Show only the chosen method's explanation (all are shown until a method is chosen).
      create.querySelectorAll("[data-method-guide]").forEach(item =>
        item.classList.toggle("is-hidden", !!method && item.dataset.methodGuide !== method));
      // Cash: no reference field at all (disabled, so nothing is sent). Card / bank: optional, method-specific text.
      const box = create.querySelector("[data-reference-field]");
      const reference = field("reference");
      if (box && reference) {
        box.hidden = method === "CASH";
        reference.disabled = method === "CASH";
        const label = box.querySelector("[data-reference-label]");
        const help = box.querySelector("[data-reference-help]");
        if (method === "CARD") {
          label.textContent = "Terminal transaction reference (optional)";
          help.textContent = "Copy the reference or approval code printed on the card machine slip, if you have it. Leave it blank if you don't.";
          reference.placeholder = "e.g. Ref 004521";
        } else if (method === "BANK_TRANSFER") {
          label.textContent = "Bank transfer reference (recommended)";
          help.textContent = "Find it in your banking app or on the deposit slip (\"Reference number\" or \"Transaction ID\"). Without it, the bakery must match your transfer by amount and date, which can take longer.";
          reference.placeholder = "e.g. TRF-2026-0001";
        }
      }
    };
    const choices = create.querySelector("[data-method-choices]");
    if (choices) choices.hidden = false;
    create.querySelectorAll("[data-method-choice]").forEach(button => button.addEventListener("click",() => {
      field("method").value = button.dataset.methodChoice;
      field("method").dispatchEvent(new Event("change",{bubbles:true}));
    }));
    create.addEventListener("input",update); create.addEventListener("change",update); update();
  }
  const rows = Array.from(document.querySelectorAll("[data-payment-row]"));
  const pagination = document.querySelector("[data-pagination]");
  if (pagination && rows.length > 10) {
    let current = 0;
    const size = 10;
    const previous = pagination.querySelector("[data-page-prev]");
    const next = pagination.querySelector("[data-page-next]");
    const render = () => {
      rows.forEach((row,index) => row.hidden = index < current * size || index >= (current + 1) * size);
      pagination.querySelector("[data-page-label]").textContent =
        "Showing " + (current * size + 1) + "–" + Math.min((current + 1) * size,rows.length) + " of " + rows.length + " records";
      previous.disabled = current === 0; next.disabled = (current + 1) * size >= rows.length;
    };
    previous.addEventListener("click",() => {current--;render();});
    next.addEventListener("click",() => {current++;render();});
    pagination.hidden = false; render();
  }
  document.querySelector("[data-print-invoice]")?.addEventListener("click",() => window.print());
  const deletion = document.querySelector('form[data-payment-form="delete"]');
  const dialogNode = document.getElementById("payment-delete-dialog");
  let approved = false;
  if (deletion && dialogNode && window.bootstrap?.Modal) {
    const dialog = new window.bootstrap.Modal(dialogNode);
    deletion.dataset.modalConfirm = "true";
    deletion.addEventListener("submit",event => {
      if (event.defaultPrevented || approved) return;
      event.preventDefault(); dialog.show();
    });
    dialogNode.querySelector("[data-confirm-delete]").addEventListener("click",() => {
      approved = true; dialog.hide(); deletion.requestSubmit();
    });
    dialogNode.addEventListener("hidden.bs.modal",() => { deletion.querySelector("button[type=submit],button:not([type])")?.focus(); });
  }
  // Admin dashboard: Delete buttons in the Actions column. The dialog shows the payment ID, amount and status;
  // only "Yes, delete payment" adds confirmed=true and submits the POST (with its CSRF tokens).
  const rowForms = Array.from(document.querySelectorAll("form[data-row-delete]"));
  if (rowForms.length) {
    const rowDialog = document.getElementById("payment-row-delete-dialog");
    const modal = rowDialog && window.bootstrap?.Modal ? new window.bootstrap.Modal(rowDialog) : null;
    let pending = null;
    const approve = form => {
      const input = document.createElement("input");
      input.type = "hidden"; input.name = "confirmed"; input.value = "true";
      form.appendChild(input); form.dataset.approved = "true"; form.submit();
    };
    rowForms.forEach(form => form.addEventListener("submit", event => {
      if (form.dataset.approved === "true") return;
      event.preventDefault();
      const {paymentId, paymentAmount, paymentStatus} = form.dataset;
      if (!modal) {
        if (window.confirm("Delete payment " + paymentId + " (" + paymentAmount + ", " + paymentStatus + ") permanently?")) approve(form);
        return;
      }
      pending = form;
      rowDialog.querySelector("[data-delete-id]").textContent = paymentId;
      rowDialog.querySelector("[data-delete-amount]").textContent = paymentAmount;
      rowDialog.querySelector("[data-delete-status]").textContent = paymentStatus;
      modal.show();
    }));
    rowDialog?.querySelector("[data-confirm-row-delete]")?.addEventListener("click", () => {
      if (!pending) return;
      const form = pending; pending = null; modal.hide(); approve(form);
    });
    rowDialog?.addEventListener("hidden.bs.modal", () => {
      if (pending) { pending.querySelector("button")?.focus(); pending = null; }
    });
  }
  document.querySelectorAll("form[data-payment-form]").forEach(form => {
    if (form.querySelector(".is-invalid")) form.querySelector(".is-invalid").focus();
    form.addEventListener("submit",event => {
      if (event.defaultPrevented) return;
      form.setAttribute("aria-busy","true");
      form.querySelectorAll("button[type=submit],button:not([type])").forEach(button => {
        button.dataset.originalHtml = button.innerHTML;
        button.disabled = true;
        const spinner = document.createElement("span"); spinner.className = "spinner-border spinner-border-sm";
        spinner.setAttribute("aria-hidden","true");
        button.replaceChildren(spinner,document.createTextNode(button.dataset.loadingLabel || "Loading..."));
      });
    });
  });
  window.addEventListener("pageshow",() => document.querySelectorAll('form[aria-busy="true"]').forEach(form => {
    form.removeAttribute("aria-busy");
    form.querySelectorAll("button[data-original-html]").forEach(button => {
      button.innerHTML = button.dataset.originalHtml; button.disabled = false; delete button.dataset.originalHtml;
    });
  }));
})();
