/*
 * Storefront enhancements. Everything is optional: without JavaScript all products
 * are shown and every link still works.
 */
(() => {
    "use strict";

    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    /* Header shadow once the page is scrolled. */
    const header = document.querySelector(".sf-header");
    if (header) {
        const onScroll = () => header.classList.toggle("is-scrolled", window.scrollY > 8);
        window.addEventListener("scroll", onScroll, { passive: true });
        onScroll();
    }

    /* Product category filter (homepage demo catalogue). */
    const grid = document.getElementById("product-grid");
    if (grid) {
        const products = Array.from(grid.querySelectorAll(".sf-product"));
        const chips = Array.from(document.querySelectorAll(".sf-chip[data-filter]"));
        const empty = document.getElementById("product-empty");
        const count = document.getElementById("product-count");

        const applyFilter = (category) => {
            let visible = 0;
            products.forEach((card) => {
                const show = category === "all" || card.dataset.category === category;
                card.hidden = !show;
                if (show) visible++;
            });
            chips.forEach((chip) => {
                const active = chip.dataset.filter === category;
                chip.classList.toggle("is-active", active);
                chip.setAttribute("aria-pressed", String(active));
            });
            if (empty) empty.hidden = visible !== 0;
            if (count) count.textContent = `Showing ${visible} ${visible === 1 ? "product" : "products"}`;
        };

        chips.forEach((chip) => chip.addEventListener("click", () => applyFilter(chip.dataset.filter)));

        // Category tiles above the grid jump to the grid with that filter applied.
        document.querySelectorAll("[data-category-link]").forEach((link) => {
            link.addEventListener("click", (event) => {
                event.preventDefault();
                applyFilter(link.dataset.categoryLink);
                const target = document.getElementById("featured");
                target.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "start" });
                const chip = chips.find((c) => c.dataset.filter === link.dataset.categoryLink);
                if (chip) chip.focus({ preventScroll: true });
            });
        });
    }

    /* Gentle reveal of sections as they scroll into view. */
    if (!reduceMotion && "IntersectionObserver" in window) {
        const targets = document.querySelectorAll(
            ".sf-cat-tiles, .sf-product, .sf-promo, .sf-showcase > *, .sf-step"
        );
        if (targets.length) {
            document.documentElement.classList.add("sf-js");
            const observer = new IntersectionObserver((entries) => {
                entries.forEach((entry) => {
                    if (entry.isIntersecting) {
                        const el = entry.target;
                        el.classList.add("is-visible");
                        observer.unobserve(el);
                        // Once revealed, hand control back to the element's own hover transitions.
                        el.addEventListener("transitionend", () => {
                            el.classList.remove("sf-reveal", "is-visible");
                            el.style.transitionDelay = "";
                        }, { once: true });
                    }
                });
            }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
            targets.forEach((el, i) => {
                el.classList.add("sf-reveal");
                el.style.transitionDelay = `${(i % 4) * 70}ms`;
                observer.observe(el);
            });
        }
    }
})();
