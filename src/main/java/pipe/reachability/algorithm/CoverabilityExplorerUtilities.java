package pipe.reachability.algorithm;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import uk.ac.imperial.pipe.exceptions.InvalidRateException;
import uk.ac.imperial.pipe.models.petrinet.Transition;
import uk.ac.imperial.state.ClassifiedState;
import uk.ac.imperial.state.HashedClassifiedState;
import uk.ac.imperial.state.HashedStateBuilder;

import java.util.*;

/**
 * This class wraps an ExplorerUtilities with coverability logic.
 * <p/>
 * Coverability logic follows that given a path x1, x2, ...., xn
 * if for any x < xn xn has tokens greater than or equal to all
 * other token counts then the Petri net will be unbounded and have an
 * infite state space.
 * <p/>
 * To kirb this explosion we bound the state to have MAX_INT tokens
 * and limit its successors
 */
public final class CoverabilityExplorerUtilities implements ExplorerUtilities {


    /**
     * Reachability graph explorer utilities
     */
    private final ExplorerUtilities explorerUtilities;

    /**
     * Used to save the parents of classified state when exploring
     */
    private final Multimap<ClassifiedState, ClassifiedState> parents = HashMultimap.create();

    /**
     * Takes a copy of the Petri net to use for state space exploration so
     * not to affect the reference
     *
     * @param utilities explorer utility to wrap with bounded state info
     */
    public CoverabilityExplorerUtilities(ExplorerUtilities utilities) {
        explorerUtilities = utilities;
    }

    /**
     * Finds the successors of the state and registers its parents in the parents multi map
     *
     * @param state state in the Petri net to find successors of
     * @return successors that have potentially been bounded
     */
    @Override
    public Map<ClassifiedState, Collection<Transition>> getSuccessorsWithTransitions(ClassifiedState state) {
        Map<ClassifiedState, Collection<Transition>> successors = explorerUtilities.getSuccessorsWithTransitions(state);
        Map<ClassifiedState, Collection<Transition>> boundedSuccessors = boundSuccessors(state, successors);
        registerParent(state, boundedSuccessors.keySet());
        return boundedSuccessors;
    }

    /**
     * Looks to see if any of the successors of the given state are unbounded and if so
     * it will bound them. This is equivalent to setting the unbounded token count to infinity (or max int in Java)
     *
     * @param state
     * @param successors
     * @return
     */
    private Map<ClassifiedState, Collection<Transition>> boundSuccessors(ClassifiedState state,
                                                                         Map<ClassifiedState, Collection<Transition>> successors) {
        Map<ClassifiedState, Collection<Transition>> boundedSuccessors = new HashMap<>();
        for (Map.Entry<ClassifiedState, Collection<Transition>> entry : successors.entrySet()) {
            ClassifiedState successor = entry.getKey();
            ClassifiedState bounded = getBoundedState(state, successor);
            boundedSuccessors.put(bounded, entry.getValue());
        }
        return boundedSuccessors;
    }

    /**
     * @param parent
     * @param state
     * @return the given state if it does not need bounding, or it bounds the tokens which can be infinite to max int
     */
    private ClassifiedState getBoundedState(ClassifiedState parent, ClassifiedState state) {
        Queue<ClassifiedState> ancestors = new ArrayDeque<>();
        Set<ClassifiedState> exploredAncestors = new HashSet<>();

        ancestors.add(parent);
        while (!ancestors.isEmpty()) {
            ClassifiedState ancestor = ancestors.poll();
            if (isUnbounded(state, ancestor)) {
                return boundState(state, ancestor);
            }
            exploredAncestors.add(ancestor);
            for (ClassifiedState p : parents.get(ancestor)) {
                if (!exploredAncestors.contains(p)) {
                    ancestors.add(p);
                }
            }
        }
        return state;
    }

    /**
     * Works by looking at the ancestors and seeing if for every token in every place the ancestor has
     * less tokens than the given state. If any ancestors do not then the state is considered bounded
     * <p/>
     * E.g. for a given state (0, 1) the ancestor (1, 0) is bounded because the first value of the ancestor is
     * larger, but if is an ancestor (0, 0) too then the state is unbounded because all of its tokens are >= the state
     *
     * @param state
     * @param ancestor
     * @return true if the given state is unbounded (ie will produce an infinite state space)
     */
    private boolean isUnbounded(ClassifiedState state, ClassifiedState ancestor) {
        for (String place : state.getPlaces()) {
            for (Map.Entry<String, Integer> entry : state.getTokens(place).entrySet()) {
                String token = entry.getKey();
                int stateCount = entry.getValue();
                int ancestorCount = ancestor.getTokens(place).get(token);
                if (ancestorCount > stateCount) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Binds the state by working out which place and which token fails the ancestor rule
     *
     * @param state
     * @param ancestor
     * @return bounded state
     */
    private ClassifiedState boundState(ClassifiedState state, ClassifiedState ancestor) {
        HashedStateBuilder builder = new HashedStateBuilder();
        for (String place : state.getPlaces()) {
            for (Map.Entry<String, Integer> entry : state.getTokens(place).entrySet()) {
                String token = entry.getKey();
                int stateCount = entry.getValue();
                int ancestorCount = ancestor.getTokens(place).get(token);
                if (ancestorCount >= stateCount) {
                    builder.placeWithToken(place, token, stateCount);
                } else {
                    builder.placeWithToken(place, token, Integer.MAX_VALUE);
                }
            }
        }
        if (state.isTangible()) {
            return HashedClassifiedState.tangibleState(builder.build());
        }
        return HashedClassifiedState.vanishingState(builder.build());
    }

    /**
     * Registers state as parents successors
     *
     * @param state
     * @param successors
     */
    private void registerParent(ClassifiedState state, Collection<ClassifiedState> successors) {
        for (ClassifiedState successor : successors) {
            if (!isBackArc(successor, state)) {
                parents.put(successor, state);
            }
        }
    }

    /**
     *
     * @param successor
     * @param state
     * @return true if this is a back arc from a successor to a parent state
     */
    private boolean isBackArc(ClassifiedState successor, ClassifiedState state) {
        Queue<ClassifiedState> ancestors = new ArrayDeque<>();
        Set<ClassifiedState> exploredAncestors = new HashSet<>();

        ancestors.add(state);
        while (!ancestors.isEmpty()) {
            ClassifiedState ancestor = ancestors.poll();
            if (ancestor.equals(successor)) {
                return true;
            }
            exploredAncestors.add(ancestor);
            for (ClassifiedState p : parents.get(ancestor)) {
                if (!exploredAncestors.contains(p)) {
                    ancestors.add(p);
                }
            }
        }
        return false;
    }

    /**
     * @param state state in the Petri net to find successors of
     * @return bound successors of state
     */
    @Override
    public Collection<ClassifiedState> getSuccessors(ClassifiedState state) {
        return getSuccessorsWithTransitions(state).keySet();
    }

    /**
     * @param state
     * @param successor
     * @return the rate at which state transitions to successor in the underlying Petri net
     * @throws InvalidRateException
     */
    //TODO: This currently does not work for bound states :( Ask Will what to do
    @Override
    public double rate(ClassifiedState state, ClassifiedState successor) throws InvalidRateException {
        return explorerUtilities.rate(state, successor);
    }
    
    /**
     * @param state
     * @param successor
     * @return names of the transitions between this state and the next state
     */
    @Override
    public Collection<String> transitionNames(ClassifiedState state, ClassifiedState successor) {
    	return explorerUtilities.transitionNames(state, successor);
    }

    /**
     * @return the underlying state of the Petri net
     */
    @Override
    public ClassifiedState getCurrentState() {
        return explorerUtilities.getCurrentState();
    }

    /**
     * @param state     initial state
     * @param successor successor state, must be directly reachable from the state
     * @return transitions that when enabled will cause state to transition to successor
     */
    @Override
    public Collection<Transition> getTransitions(ClassifiedState state, ClassifiedState successor) {
        return explorerUtilities.getTransitions(state, successor);
    }

    /**
     * @param state
     * @param transitions
     * @return the weight of the transitions from the state
     * @throws InvalidRateException
     */
    @Override
    public double getWeightOfTransitions(ClassifiedState state, Iterable<Transition> transitions)
            throws InvalidRateException {
        return explorerUtilities.getWeightOfTransitions(state, transitions);
    }

    /**
     * @param state state in the Petri net to determine enabled transitions of
     * @return all transitions which are enabled when in this state
     */
    @Override
    public Collection<Transition> getAllEnabledTransitions(ClassifiedState state) {
        return explorerUtilities.getAllEnabledTransitions(state);
    }

    /**
     * Clears the explorer utilities cache
     */
    @Override
    public void clear() {
        explorerUtilities.clear();
    }

    /**
     * Coverability graph turns an infinite state space into a finite
     * space via bounding states so it is always possible that a state can continue
     *
     * @param stateCount
     * @return true
     */
    @Override
    public boolean canExploreMore(int stateCount) {
        return true;
    }
}
