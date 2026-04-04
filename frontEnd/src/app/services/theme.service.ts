import { Injectable, signal, effect, PLATFORM_ID, Inject, computed } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export type Theme = 'light' | 'dark' | 'system';
export type TimeIntensity = 'cool' | 'warm' | 'hot' | 'normal';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly theme = signal<Theme>('system');
  private readonly systemTheme = signal<'light' | 'dark'>('light');
  private readonly intensity = signal<TimeIntensity>('normal');
  private readonly isBrowser: boolean;

  private readonly effectiveTheme = computed(() => {
    const theme = this.theme();
    if (theme === 'system') {
      return this.systemTheme();
    }
    return theme;
  });

  constructor(@Inject(PLATFORM_ID) platformId: object) {
    this.isBrowser = isPlatformBrowser(platformId);

    if (this.isBrowser) {
      this.updateIntensity();
      // Update intensity every minute
      setInterval(() => this.updateIntensity(), 60000);

      const media = window.matchMedia('(prefers-color-scheme: dark)');
      this.systemTheme.set(media.matches ? 'dark' : 'light');

      this.theme.set(this.getStoredTheme());

      effect(() => {
        const currentTheme = this.theme();
        const currentIntensity = this.intensity();
        this.applyTheme(currentTheme, currentIntensity);
        localStorage.setItem('app-theme', currentTheme);
      });

      // Listen to system changes
      media.addEventListener('change', (e) => {
        this.systemTheme.set(e.matches ? 'dark' : 'light');
        this.applyTheme(this.theme(), this.intensity());
      });
    }
  }

  private updateIntensity() {
    const hour = new Date().getHours();
    let intensity: TimeIntensity = 'normal';

    if (hour >= 0 && hour < 6) {
      intensity = 'cool';      // Midnight
    } else if (hour >= 6 && hour < 12) {
      intensity = 'warm';      // Morning
    } else if (hour >= 12 && hour < 15) {
      intensity = 'hot';       // Afternoon
    } else {
      intensity = 'normal';    // Evening
    }
    this.intensity.set(intensity);
  }

  toggleTheme() {
    const current = this.theme();
    let next: Theme;

    if (current === 'system') {
      const isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      next = isDark ? 'light' : 'dark';
    } else {
      next = current === 'light' ? 'dark' : 'light';
    }

    this.theme.set(next);
  }

  setTheme(theme: Theme) {
    this.theme.set(theme);
  }

  getTheme() {
    return this.theme.asReadonly();
  }

  getEffectiveTheme() {
    return this.effectiveTheme;
  }

  getTimeIntensity() {
    return this.intensity.asReadonly();
  }

  private applyTheme(theme: Theme, intensity: TimeIntensity) {
    if (!this.isBrowser) return;

    let effectiveTheme = theme;
    if (theme === 'system') {
      effectiveTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    document.documentElement.setAttribute('data-theme', effectiveTheme);
    document.documentElement.setAttribute('data-intensity', intensity);
    // Dispatch custom event so other components can react if they don't use the service
    window.dispatchEvent(new Event('theme-changed'));
  }

  private getStoredTheme(): Theme {
    if (this.isBrowser) {
      return (localStorage.getItem('app-theme') as Theme) || 'system';
    }
    return 'system';
  }
}
