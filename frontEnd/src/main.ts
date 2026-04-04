import { bootstrapApplication } from '@angular/platform-browser';
import { appComponentConfig } from '@app/app.component.config';
import { AppComponent } from '@app/app.component';

bootstrapApplication(AppComponent, appComponentConfig)
  .catch((err) => console.error(err));
