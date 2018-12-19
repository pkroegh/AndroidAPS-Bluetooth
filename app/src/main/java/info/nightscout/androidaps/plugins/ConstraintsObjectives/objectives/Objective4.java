package info.nightscout.androidaps.plugins.ConstraintsObjectives.objectives;

import java.util.List;

import info.nightscout.androidaps.R;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.plugins.ConstraintsSafety.SafetyPlugin;
import info.nightscout.utils.T;

public class Objective4 extends Objective {

    public Objective4() {
        super(3, R.string.objectives_3_objective, R.string.objectives_3_gate);
    }

    @Override
    protected void setupTasks(List<Task> tasks) {
        tasks.add(new MinimumDurationTask(T.days(5).msecs()));
        tasks.add(new Task(R.string.closedmodeenabled) {
            @Override
            public boolean isCompleted() {
                Constraint<Boolean> closedLoopEnabled = new Constraint<>(true);
                SafetyPlugin.getPlugin().isClosedLoopAllowed(closedLoopEnabled);
                return closedLoopEnabled.value();
            }
        });
    }
}
