package pipe.reachability.algorithm.parallel;

import pipe.reachability.algorithm.ExplorerUtilities;
import pipe.reachability.algorithm.StateRecord;
import pipe.reachability.algorithm.TimelessTrapException;
import pipe.reachability.algorithm.VanishingExplorer;
import uk.ac.imperial.pipe.exceptions.InvalidRateException;
import uk.ac.imperial.state.ClassifiedState;
import uk.ac.imperial.utils.Pair;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;


/**
 * Callable worker that is given a state to explore and calculates the successors
 * of the state.
 *
 */
public final class ParallelStateExplorer implements Callable<Map<ClassifiedState, Pair<Double, Collection<String>>>> {

    /**
     * Count down latch, this value is decremented once the call method
     * has finished processing
     */
    private final CountDownLatch latch;

    /**
     * State to explore successors of in call method
     */
    private final ClassifiedState state;

    /**
     * Utilities associated with the Petri net that the state belongs to.
     * They provide a means to query for the states successors
     */
    private final ExplorerUtilities explorerUtilities;

    /**
     * Describes how to explore vanishing states
     */
    private final VanishingExplorer vanishingExplorer;

    public ParallelStateExplorer(CountDownLatch latch, ClassifiedState state, ExplorerUtilities explorerUtilities,
                                 VanishingExplorer vanishingExplorer) {

        this.latch = latch;
        this.state = state;
        this.explorerUtilities = explorerUtilities;
        this.vanishingExplorer = vanishingExplorer;
    }

    /**
     * Performs state space exploration of the given state
     *
     * @return successors
     */
    @Override
    public Map<ClassifiedState, Pair<Double, Collection<String>>> call() throws TimelessTrapException, InvalidRateException {
        try {
            Map<ClassifiedState, Pair<Double, Collection<String>>> states = new HashMap<>();
            for (ClassifiedState successor : explorerUtilities.getSuccessors(state)) {
                double rate = explorerUtilities.rate(state, successor);
                Collection<String> transitionNames = explorerUtilities.transitionNames(state, successor);
                
                if (successor.isTangible()) {
                    Pair<Double, Collection<String>> pair = new Pair<>(rate, transitionNames);
                	registerState(successor, pair, states);
                } else {
                    Collection<StateRecord> explorableStates = vanishingExplorer.explore(successor, rate, transitionNames);
                    for (StateRecord record : explorableStates) {
                    	registerState(record.getState(), record.getPair(), states);
                    }
                }
            }
            return states;
        } finally {
            latch.countDown();
        }
    }
    
    private void registerState(ClassifiedState successor, Pair<Double, Collection<String>> pair, Map<ClassifiedState, Pair<Double, Collection<String>>> states) {
    	if (states.containsKey(successor)) {
    		double rate = states.get(successor).getLeft() + pair.getLeft();
    		Collection<String> names = states.get(successor).getRight();
    		names.addAll(pair.getRight());
    		
    		Pair<Double, Collection<String>> newPair = new Pair<>(rate, names);
    		states.put(successor, newPair);
    	} else {
    		states.put(successor, pair);
    	}
    }
}
