/**
 * Central Theme Manager for Online Auction Simulator
 * Supports Light & Dark themes with localStorage persistence and OS preference fallback.
 */
(function() {
    const THEME_STORAGE_KEY = 'auctionTheme';
    const THEMES = { LIGHT: 'light', DARK: 'dark' };

    function getPreferredTheme() {
        try {
            const savedTheme = localStorage.getItem(THEME_STORAGE_KEY);
            if (savedTheme === THEMES.LIGHT || savedTheme === THEMES.DARK) {
                return savedTheme;
            }
        } catch {
            // localStorage unavailable (e.g. strict privacy mode)
        }
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            return THEMES.DARK;
        }
        return THEMES.LIGHT;
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        const metaThemeColor = document.querySelector('meta[name="theme-color"]');
        if (metaThemeColor) {
            metaThemeColor.setAttribute('content', theme === THEMES.DARK ? '#0f172a' : '#ffffff');
        }
        updateToggleButtons(theme);
    }

    function saveTheme(theme) {
        try {
            localStorage.setItem(THEME_STORAGE_KEY, theme);
        } catch {
            // Ignore storage quota or permission errors
        }
    }

    function updateToggleButtons(theme) {
        const toggleButtons = document.querySelectorAll('.theme-toggle-btn');
        toggleButtons.forEach((btn) => {
            const isDark = theme === THEMES.DARK;
            btn.setAttribute('aria-label', isDark ? 'Switch to light theme' : 'Switch to dark theme');
            btn.setAttribute('title', isDark ? 'Switch to light theme' : 'Switch to dark theme');
            btn.setAttribute('aria-pressed', String(isDark));
        });
    }

    function toggleTheme() {
        const currentTheme = document.documentElement.getAttribute('data-theme') || THEMES.LIGHT;
        const newTheme = currentTheme === THEMES.DARK ? THEMES.LIGHT : THEMES.DARK;
        applyTheme(newTheme);
        saveTheme(newTheme);
    }

    // Apply immediately to prevent flash of wrong theme before DOM finishes loading
    const initialTheme = getPreferredTheme();
    applyTheme(initialTheme);

    // Initialize toggle button event listeners when DOM is interactive
    function initToggleButtons() {
        const toggleButtons = document.querySelectorAll('.theme-toggle-btn');
        toggleButtons.forEach((btn) => {
            btn.removeEventListener('click', toggleTheme);
            btn.addEventListener('click', toggleTheme);
        });
        updateToggleButtons(document.documentElement.getAttribute('data-theme') || initialTheme);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initToggleButtons);
    } else {
        initToggleButtons();
    }

    // Listen to system theme changes if user has not explicitly set a preference
    if (window.matchMedia) {
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
            let hasSaved = false;
            try {
                hasSaved = Boolean(localStorage.getItem(THEME_STORAGE_KEY));
            } catch {}
            if (!hasSaved) {
                applyTheme(e.matches ? THEMES.DARK : THEMES.LIGHT);
            }
        });
    }

    // Expose toggle function globally if needed
    window.toggleAuctionTheme = toggleTheme;
})();

