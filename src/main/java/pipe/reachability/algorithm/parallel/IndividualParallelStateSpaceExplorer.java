package pipe.reachability.algorithm.parallel;

import pipe.reachability.algorithm.AbstractStateSpaceExplorer;
import pipe.reachability.algorithm.ExplorerUtilities;
import pipe.reachability.algorithm.TimelessTrapException;
import pipe.reachability.algorithm.VanishingExplorer;
import uk.ac.imperial.io.StateProcessor;
import uk.ac.imperial.state.ClassifiedState;
import uk.ac.imperial.utils.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.Collection;

/**
 * This explores individual states on seperate threads and then joins
 * their results together on the master thread.
 * <p/>
 * Yields speed ups for larger state spaces but is marginally slower for smaller ones
 */
public final class IndividualParallelStateSpaceExplorer extends AbstractStateSpaceExplorer {
    /**
     * The number of threads to use
     */
    private static final int THREADS = 8;

    /**
     * Used for submitting tasks to
     */
    protected ExecutorService executorService;

    /**
     * Constructor for creating the state space explorer
     * @param stateProcessor
     * @param vanishingExplorer
     * @param explorerUtilities
     */
    public IndividualParallelStateSpaceExplorer(StateProcessor stateProcessor, VanishingExplorer vanishingExplorer,
                                                ExplorerUtilities explorerUtilities) {
        super(explorerUtilities, vanishingExplorer, stateProcessor);
        executorService = Executors.newFixedThreadPool(THREADS);

    }

    /**
     * Explores the state space one state at a time on multiple threads
     *
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws TimelessTrapException
     */
    @Override
    protected void stateSpaceExploration() throws InterruptedException, ExecutionException, TimelessTrapException {
        if (executorService.isTerminated()) {
            executorService = Executors.newFixedThreadPool(THREADS);
        }
        int elemsAtCurrentLevel = explorationQueue.size();
        int elemsAtNextLevel = 0;
        while (!explorationQueue.isEmpty()) {

            Map<ClassifiedState, Future<Map<ClassifiedState, Pair<Double, Collection<String>>>>> successorFutures = new HashMap<>();
            CountDownLatch latch = new CountDownLatch(elemsAtCurrentLevel);
            for (int i = 0; i < elemsAtCurrentLevel; i++) {
                ClassifiedState state = explorationQueue.poll();
                successorFutures.put(state, executorService.submit(
                        new ParallelStateExplorer(latch, state, explorerUtilities, vanishingExplorer)));
            }

            latch.await();
            for (Map.Entry<ClassifiedState, Future<Map<ClassifiedState, Pair<Double, Collection<String>>>>> entry : successorFutures.entrySet()) {
                Future<Map<ClassifiedState, Pair<Double, Collection<String>>>> future = entry.getValue();
                successors.clear();

                try {
                    Map<ClassifiedState, Pair<Double, Collection<String>>> successorsData = future.get();
                    for (Map.Entry<ClassifiedState, Pair<Double, Collection<String>>> successorEntry : successorsData.entrySet()) {
                        ClassifiedState successor = successorEntry.getKey();
                        double rate = successorEntry.getValue().getLeft();
                        Collection<String> transitionNames = successorEntry.getValue().getRight();
                        
                        Pair<Double, Collection<String>> pair = new Pair<>(rate, transitionNames);
                        registerState(successor, pair);
                        if (!explored.contains(successor)) {
                            elemsAtNextLevel++;
                            explorationQueue.add(successor);
                            markAsExplored(successor);
                        }
                    }
                } catch (ExecutionException ee) {
                    throw new TimelessTrapException(ee);
                }
                ClassifiedState state = entry.getKey();
                writeStateTransitions(state, successors);
            }
            elemsAtCurrentLevel = elemsAtNextLevel;
            elemsAtNextLevel = 0;

        }
        executorService.shutdownNow();

    }

}
