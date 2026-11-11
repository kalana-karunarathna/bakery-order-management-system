/*
 * Order Management (Component 03) enhancements.
 * Everything here is progressive: every form still submits normally without JavaScript.
 * Totals shown in the browser are previews only. The server calculates the stored total.
 */
(() => {
    "use strict";

    const money = (value) =>
        "LKR " + value.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    const num = (value) => {
        const n = parseFloat(value);
        return Number.isFinite(n) ? n : NaN;
    };

    /* ── Field validation helpers ─────────────────────────────────── */

    function setError(field, message) {
        const box = document.getElementById(field.id + "-error");
        if (message) {
            field.classList.add("is-invalid");
            field.setAttribute("aria-invalid", "true");
            if (box) box.textContent = message;
        } else {
            field.classList.remove("is-invalid");
            field.removeAttribute("aria-invalid");
            if (box) box.textContent = "";
        }
    }

    function quantityError(input) {
        const raw = input.value.trim();
        if (raw === "") return "Please enter a quantity.";
        const q = Number(raw);
        if (!Number.isInteger(q)) return "Quantity must be a whole number.";
        if (q < 1) return "Quantity must be at least 1.";
        if (q > 100) return "For more than 100 items, please contact the bakery.";
        return "";
    }

    function validate(form, rules) {
        let firstInvalid = null;
        rules.forEach(([field, check]) => {
            if (!field) return;
            const message = check(field);
            setError(field, message);
            if (message && !firstInvalid) firstInvalid = field;
        });
        if (firstInvalid) firstInvalid.focus();
        return !firstInvalid;
    }

    /** Disables the submit button once a valid form is sent, to avoid double orders. */
    function markSubmitting(form) {
        const button = form.querySelector('button[type="submit"]');
        if (!button) return;
        button.dataset.originalHtml = button.innerHTML;
        button.disabled = true;
        if (button.dataset.submitLabel) button.textContent = button.dataset.submitLabel;
    }

    // Restore buttons if the page is shown again from the back/forward cache.
    window.addEventListener("pageshow", () => {
        document.querySelectorAll("button[data-original-html]").forEach((button) => {
            button.disabled = false;
            button.innerHTML = button.dataset.originalHtml;
            delete button.dataset.originalHtml;
        });
    });

    /* ── Quantity stepper ─────────────────────────────────────────── */

    document.querySelectorAll("[data-stepper]").forEach((stepper) => {
        const input = stepper.querySelector('input[type="number"]');
        stepper.querySelectorAll("[data-step]").forEach((button) => {
            button.addEventListener("click", () => {
                const current = parseInt(input.value, 10);
                const next = Math.max(1, (Number.isFinite(current) ? current : 0) + Number(button.dataset.step));
                input.value = String(next);
                input.dispatchEvent(new Event("input", { bubbles: true }));
            });
        });
    });

    /* ── Place order ──────────────────────────────────────────────── */

    const placeForm = document.querySelector('[data-order-form="place"]');
    if (placeForm) {
        const fee = num(placeForm.dataset.deliveryFee) || 0;
        // Demo menu: the chosen product card carries its name and price (display only; the server sets the real price).
        const picker = placeForm.querySelector("#productPicker");
        const selected = () => placeForm.querySelector('input[name="productId"]:checked');
        const f = {
            quantity: placeForm.querySelector("#quantity"),
            delivery: placeForm.querySelector("#typeDelivery"),
            address: placeForm.querySelector("#deliveryAddress"),
            addressGroup: placeForm.querySelector("#deliveryAddressGroup"),
        };
        const out = (key) => placeForm.querySelector(`[data-summary="${key}"]`);

        const isDelivery = () => f.delivery.checked;

        function syncDelivery(animate) {
            const show = isDelivery();
            const wasHidden = f.addressGroup.hidden;
            f.addressGroup.hidden = !show;
            f.address.required = show;
            if (!show) setError(f.address, "");
            if (show && wasHidden && animate) {
                f.addressGroup.classList.remove("is-entering");
                void f.addressGroup.offsetWidth; // restart animation
                f.addressGroup.classList.add("is-entering");
            }
        }

        function updateSummary() {
            const choice = selected();
            const name = choice ? choice.dataset.name : "";
            const nameOut = out("productName");
            nameOut.textContent = name || "No product selected yet";
            nameOut.classList.toggle("is-placeholder", !name);
            out("productId").textContent = choice ? choice.value : "—";
            const image = out("image");
            if (image) {
                image.hidden = !choice;
                if (choice) image.src = choice.dataset.image;
                out("icon").style.display = choice ? "none" : "";
            }

            const qty = parseInt(f.quantity.value, 10);
            const price = choice ? num(choice.dataset.price) : 0;
            const qtyOk = Number.isInteger(qty) && qty >= 1;
            const priceOk = price > 0;
            const deliveryFee = isDelivery() ? fee : 0;

            out("quantity").textContent = qtyOk ? String(qty) : "—";
            out("unitPrice").textContent = priceOk ? money(price) : "—";
            out("deliveryFee").textContent = isDelivery() ? money(fee) : "Free (pickup)";

            if (qtyOk && priceOk) {
                const subtotal = qty * price;
                out("subtotal").textContent = money(subtotal);
                out("total").textContent = money(subtotal + deliveryFee);
            } else {
                out("subtotal").textContent = "—";
                out("total").textContent = "—";
            }
        }

        placeForm.addEventListener("input", (event) => {
            if (event.target.classList.contains("is-invalid")) setError(event.target, "");
            updateSummary();
        });
        placeForm.addEventListener("change", (event) => {
            if (event.target.name === "orderType") syncDelivery(true);
            if (event.target.name === "productId") setError(picker, "");
            updateSummary();
        });

        placeForm.addEventListener("submit", (event) => {
            const ok = validate(placeForm, [
                [picker, () => (selected() ? "" : "Please choose a product from the menu.")],
                [f.quantity, quantityError],
                [f.address, (field) =>
                    isDelivery() && field.value.trim() === "" ? "Please enter a delivery address." : ""],
            ]);
            if (!ok && !selected()) placeForm.querySelector('input[name="productId"]')?.focus();
            if (!ok) {
                event.preventDefault();
                return;
            }
            markSubmitting(placeForm);
        });

        syncDelivery(false);
        updateSummary();
    }

    /* ── Edit order ───────────────────────────────────────────────── */

    const editForm = document.querySelector('[data-order-form="edit"]');
    if (editForm) {
        const unitPrice = num(editForm.dataset.unitPrice) || 0;
        const fee = num(editForm.dataset.deliveryFee) || 0;
        const originalQty = parseInt(editForm.dataset.originalQuantity, 10) || 0;
        const quantity = editForm.querySelector("#quantity");
        const address = editForm.querySelector("#deliveryAddress");
        const out = (key) => editForm.querySelector(`[data-summary="${key}"]`);

        function updateSummary() {
            const qty = parseInt(quantity.value, 10);
            const diffOut = out("difference");
            if (!(Number.isInteger(qty) && qty >= 1)) {
                out("quantity").textContent = "—";
                out("subtotal").textContent = "—";
                out("total").textContent = "—";
                diffOut.textContent = "";
                return;
            }
            const subtotal = qty * unitPrice;
            const diff = (qty - originalQty) * unitPrice;
            out("quantity").textContent = String(qty);
            out("subtotal").textContent = money(subtotal);
            out("total").textContent = money(subtotal + fee);
            diffOut.classList.toggle("is-up", diff > 0);
            diffOut.classList.toggle("is-down", diff < 0);
            diffOut.textContent = diff === 0 ? "(no change)" : `(${diff > 0 ? "+" : "−"}${money(Math.abs(diff))})`;
        }

        editForm.addEventListener("input", (event) => {
            if (event.target.classList.contains("is-invalid")) setError(event.target, "");
            updateSummary();
        });

        editForm.addEventListener("submit", (event) => {
            const ok = validate(editForm, [
                [quantity, quantityError],
                [address, (field) => (field.value.trim() === "" ? "Please enter a delivery address." : "")],
            ]);
            if (!ok) {
                event.preventDefault();
                return;
            }
            markSubmitting(editForm);
        });

        updateSummary();
    }

    /* ── Confirmation dialog for destructive actions ──────────────── */

    const dialog = document.getElementById("orderConfirmDialog");
    let pendingForm = null;

    document.querySelectorAll("form[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (form.dataset.confirmed === "true") return;
            event.preventDefault();

            // Fallback for browsers without <dialog> support.
            if (!dialog || typeof dialog.showModal !== "function") {
                if (window.confirm(form.dataset.confirm)) {
                    form.dataset.confirmed = "true";
                    form.submit();
                }
                return;
            }

            pendingForm = form;
            dialog.querySelector("#orderConfirmTitle").textContent = form.dataset.confirmTitle || "Are you sure?";
            dialog.querySelector("#orderConfirmMessage").textContent = form.dataset.confirm;
            dialog.querySelector("[data-dialog-confirm]").textContent = form.dataset.confirmAction || "Confirm";
            dialog.querySelector("[data-dialog-cancel]").textContent = form.dataset.confirmCancel || "Keep order";
            dialog.showModal();
            dialog.querySelector("[data-dialog-cancel]").focus();
        });
    });

    if (dialog) {
        dialog.querySelector("[data-dialog-cancel]").addEventListener("click", () => dialog.close("cancel"));
        dialog.querySelector("[data-dialog-confirm]").addEventListener("click", () => dialog.close("confirm"));
        // Clicking the backdrop closes the dialog.
        dialog.addEventListener("click", (event) => {
            if (event.target === dialog) dialog.close("cancel");
        });
        dialog.addEventListener("close", () => {
            const form = pendingForm;
            pendingForm = null;
            if (form && dialog.returnValue === "confirm") {
                form.dataset.confirmed = "true";
                form.querySelectorAll('button[type="submit"]').forEach((b) => (b.disabled = true));
                form.submit();
            } else if (form) {
                const trigger = form.querySelector('button[type="submit"]');
                if (trigger) trigger.focus();
            }
        });
    }

    /* ── Admin: client-side search and status filter ──────────────── */

    const table = document.getElementById("ordersTable");
    if (table) {
        const rows = Array.from(table.querySelectorAll("tr[data-order-row]"));
        const search = document.getElementById("orderSearch");
        const statusSelect = document.getElementById("statusFilter");
        const clearButton = document.getElementById("clearFilters");
        const count = document.getElementById("orderCount");
        const noMatches = document.getElementById("noMatches");
        const statTiles = Array.from(document.querySelectorAll("[data-filter-status]"));

        function applyFilters() {
            const term = search.value.trim().toLowerCase();
            const status = statusSelect.value;
            let visible = 0;
            rows.forEach((row) => {
                const match = (!status || row.dataset.status === status) &&
                              (!term || row.dataset.search.includes(term));
                row.hidden = !match;
                if (match) visible++;
            });
            count.textContent = `Showing ${visible} of ${rows.length} ${rows.length === 1 ? "order" : "orders"}`;
            noMatches.hidden = visible !== 0;
            table.hidden = visible === 0;
            clearButton.hidden = !term && !status;
            statTiles.forEach((tile) => {
                const active = tile.dataset.filterStatus === status;
                tile.classList.toggle("is-active", active);
                tile.setAttribute("aria-pressed", String(active));
            });
        }

        search.addEventListener("input", applyFilters);
        statusSelect.addEventListener("change", applyFilters);
        clearButton.addEventListener("click", () => {
            search.value = "";
            statusSelect.value = "";
            applyFilters();
            search.focus();
        });
        statTiles.forEach((tile) => {
            tile.addEventListener("click", () => {
                const value = tile.dataset.filterStatus;
                statusSelect.value = statusSelect.value === value ? "" : value;
                applyFilters();
            });
        });

        // Only enable "Update" once a different status is chosen.
        table.querySelectorAll("form.o-status-form").forEach((form) => {
            const select = form.querySelector("select");
            const button = form.querySelector('button[type="submit"]');
            if (!select || select.disabled) return;
            const sync = () => { button.disabled = select.value === form.dataset.currentStatus; };
            select.addEventListener("change", sync);
            sync();
        });

        applyFilters();
    }
})();
