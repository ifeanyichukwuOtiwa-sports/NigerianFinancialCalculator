import { Component, input, inject } from '@angular/core';
import { ThemeService, Theme } from '../../services/theme.service';
import { NavigationService } from '../../services/navigation.service';
import * as Const from '../../app.constants';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss'
})
export class NavbarComponent {
  private readonly themeService = inject(ThemeService);
  private readonly navService = inject(NavigationService);

  title = input<string>(Const.APP_TITLE);
  activeTab = this.navService.activeTab;
  theme = this.themeService.getTheme();

  protected setTheme(theme: Theme) {
    this.themeService.setTheme(theme);
  }
}
