package my_base;

import base.Params;
import base.PeriodicLoop;

public class MyPeriodicLoop extends PeriodicLoop {

	@Override
	public void execute() {
		// Let the super class do its work first
		super.execute();
		System.out.println("Periodic loop tick, elapsedTime=" + elapsedTime());
		App.mainRouter().route("/team/doStep", Params.of(0.03));
	}
}
