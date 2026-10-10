// Small shared enhancement; navigation also works with JavaScript disabled.
document.addEventListener("DOMContentLoaded", () => {
    const currentPath = window.location.pathname.replace(/\/$/, "") || "/";
    document.querySelectorAll(".navbar a.nav-link").forEach((link) => {
        const linkPath = new URL(link.href).pathname.replace(/\/$/, "") || "/";
        if (linkPath === currentPath) {
            link.classList.add("active");
            link.setAttribute("aria-current", "page");
        }
    });
});
