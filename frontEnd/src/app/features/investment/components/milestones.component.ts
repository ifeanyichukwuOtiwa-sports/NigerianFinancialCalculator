import { Component, input, output } from '@angular/core';
import { Milestone } from '@app/shared/types/app.types';

@Component({
	selector: 'app-milestones',
	imports: [],
	templateUrl: './milestones.component.html',
	styleUrl: './milestones.component.scss',
})
export class MilestonesComponent {
	milestones = input.required<Milestone[]>();
	selectYear = output<number>();
}
