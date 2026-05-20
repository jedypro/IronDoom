package my_base;

import base.PeriodicLoop;

public class MyPeriodicLoop extends PeriodicLoop {

	private AppContent content = App.content();
	private int step = 0;

	@Override
	public void execute() {
		// Let the super class do its work first
		super.execute();
		// Then do your own work here ...
		if (step < 3) {
			System.out.println("Periodic loop step " + step);
			content.teamBackend().doStep(step);
			step++;
		}
	}
}
