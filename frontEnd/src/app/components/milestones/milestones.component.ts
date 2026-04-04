import { Component, input, output } from '@angular/core';
import { Milestone } from '../../app.types';

@Component({
  selector: 'app-milestones',
  standalone: true,
  imports: [],
  templateUrl: './milestones.component.html',
  styleUrl: './milestones.component.scss'
})
export class MilestonesComponent {
  milestones = input.required<Milestone[]>();
  selectYear = output<number>();
}
