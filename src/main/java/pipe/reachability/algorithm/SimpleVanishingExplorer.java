package pipe.reachability.algorithm;

import uk.ac.imperial.state.ClassifiedState;

import java.util.Arrays;
import java.util.Collection;

/**
 * This state performs no computation of vanishing states and simply
 * returns them to the user to be explored normally
 */
public final class SimpleVanishingExplorer implements VanishingExplorer {
    /**
     *
     * @param vanishingState vanishing state to explore.
     * @param rate rate at which vanishingState is entered from the previous state
     * @return the exact vanishing state to explore
     * @throws TimelessTrapException
     */
    @Override
    public Collection<StateRecord> explore(ClassifiedState vanishingState, double rate, Collection<String> transitionNames)
            throws TimelessTrapException {
        return Arrays.asList(new StateRecord(vanishingState, rate, transitionNames));
    }
}
