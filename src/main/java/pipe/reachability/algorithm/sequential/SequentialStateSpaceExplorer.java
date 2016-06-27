package pipe.reachability.algorithm.sequential;

import pipe.reachability.algorithm.*;
import uk.ac.imperial.io.StateProcessor;
import uk.ac.imperial.pipe.exceptions.InvalidRateException;
import uk.ac.imperial.state.ClassifiedState;
import uk.ac.imperial.utils.Pair;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class performs state space exploration sequentially to determine the reachability of each state
 * Vanishing states can be explored in numerous ways so a {@link pipe.reachability.algorithm.VanishingExplorer}
 * is used to determine how to process them.
 */
public final class SequentialStateSpaceExplorer extends AbstractStateSpaceExplorer {


    private static final Logger LOGGER = Logger.getLogger(SequentialStateSpaceExplorer.class.getName());

    /**
     * Constructor for generating a single thread state space explorer
     * @param explorerUtilities utilities to use for exploration, can be used to generate the reachability graph
     *                          or the coverability graph
     * @param vanishingExplorer exploring algorithm for processing vanishing states, can be used to include them
     *                          in the graphs or to remove them on the fly
     * @param stateProcessor processor for actually writing out the results
     */
    public SequentialStateSpaceExplorer(ExplorerUtilities explorerUtilities, VanishingExplorer vanishingExplorer,
                                        StateProcessor stateProcessor) {
        super(explorerUtilities, vanishingExplorer, stateProcessor);
    }

    /**
     * Performs state space exploration of the tangibleQueue
     * popping a state off the stack and exploring all its successors.
     * <p/>
     * It records the reachability graph into the writer
     */
    @Override
    protected void stateSpaceExploration() throws TimelessTrapException, IOException, InvalidRateException {
        int iterations = 0;
        while (!explorationQueue.isEmpty() && explorerUtilities.canExploreMore(stateCount)) {
            ClassifiedState state = explorationQueue.poll();
            successors.clear();
            for (ClassifiedState successor : explorerUtilities.getSuccessors(state)) {
                double rate = explorerUtilities.rate(state, successor);
                Collection<String> transitionNames = explorerUtilities.transitionNames(state, successor);
                Pair<Double, Collection<String>> pair = new Pair<>(rate, transitionNames);
                if (successor.isTangible()) {
                	registerStateTransition(successor, pair);
                } else {
                    Collection<StateRecord> explorableStates = vanishingExplorer.explore(successor, pair.getLeft(), pair.getRight());
                    for (StateRecord record : explorableStates) {
                        registerStateTransition(record.getState(), record.getPair());
                    }
                }
            }
            writeStateTransitions(state, successors);
            explorerUtilities.clear();
            iterations++;
        }
        LOGGER.log(Level.INFO, String.format("Took %d iterations to explore state space", iterations));
    }
}
