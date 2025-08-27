// Global theme script for all pages
function applyThemeFromStorage() {
    const themeToggle = document.getElementById("theme-toggle");
    const theme = localStorage.getItem("theme");
    if (theme === "light") {
        document.body.classList.add("light-theme");
        if (themeToggle) themeToggle.textContent = "🌙 Dark";
    } else {
        document.body.classList.remove("light-theme");
        if (themeToggle) themeToggle.textContent = "🌞 Light";
    }
}
window.addEventListener("DOMContentLoaded", () => {
    applyThemeFromStorage();
    const themeToggle = document.getElementById("theme-toggle");
    if (themeToggle) {
        themeToggle.addEventListener("click", () => {
            if (document.body.classList.contains("light-theme")) {
                document.body.classList.remove("light-theme");
                localStorage.setItem("theme", "dark");
                themeToggle.textContent = "🌞 Light";
            } else {
                document.body.classList.add("light-theme");
                localStorage.setItem("theme", "light");
                themeToggle.textContent = "🌙 Dark";
            }
        });
    }
});
