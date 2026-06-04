package my_base;

import base.Params;
import base.PeriodicLoop;

public class MyPeriodicLoop extends PeriodicLoop {
    private boolean paused = false;

    public void setPaused(boolean paused) {
        this.paused = paused;
    }
	@Override
	public void execute() {
		if (paused) return;
		// Let the super class do its work first
		super.execute();
		//System.out.println("Periodic loop tick, elapsedTime=" + elapsedTime());
		App.mainRouter().route("/team/doStep", Params.of(0.03));
	}
}
