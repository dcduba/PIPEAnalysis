package pipe.reachability.algorithm;

import uk.ac.imperial.pipe.animation.AnimationLogic;
import uk.ac.imperial.pipe.animation.PetriNetAnimationLogic;
import uk.ac.imperial.pipe.exceptions.InvalidRateException;
import uk.ac.imperial.pipe.models.petrinet.PetriNet;
import uk.ac.imperial.pipe.models.petrinet.Place;
import uk.ac.imperial.pipe.models.petrinet.Token;
import uk.ac.imperial.pipe.models.petrinet.Transition;
import uk.ac.imperial.pipe.parsers.FunctionalResults;
import uk.ac.imperial.pipe.parsers.PetriNetWeightParser;
import uk.ac.imperial.pipe.parsers.StateEvalVisitor;
import uk.ac.imperial.pipe.visitor.ClonePetriNet;
import uk.ac.imperial.state.ClassifiedState;
import uk.ac.imperial.state.HashedClassifiedState;
import uk.ac.imperial.state.HashedStateBuilder;
import uk.ac.imperial.state.State;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Useful methods to help explore the state space.
 * <p/>
 * Performs caching of frequent computations
 */
public abstract class CachingExplorerUtilities implements ExplorerUtilities {
    /**
     * Petri net to explore
     */
    private final PetriNet petriNet;
    /**
     * Animator for the Petri net
     */
    private final AnimationLogic animationLogic;


    /**
     * Cached successors is used when exploring states to quickly determine
     * a states successors it has already seen before.
     * <p/>
     * It will be most useful when exploring cyclic transitions
     */
    private Map<ClassifiedState, Map<ClassifiedState, Collection<Transition>>> cachedSuccessors =
            new ConcurrentHashMap<>();

    /**
     * Takes a copy of the Petri net to use for state space exploration so
     * not to affect the reference
     *
     * @param petriNet petri net to use for state space exploration
     */
    public CachingExplorerUtilities(PetriNet petriNet) {
        this.petriNet = ClonePetriNet.clone(petriNet);
        animationLogic = new PetriNetAnimationLogic(this.petriNet);
    }

    /**
     * Finds successors of the given state. A successor is a state that occurs
     * when one of the enabled transitions in the current state is fired.
     * <p/>
     * Performs caching of the successors to speed up computation time
     * when a state is queried more than once. This is particularly useful
     * if on the fly vanishing state exploration is used
     *
     * @param state
     * @return map of successor states to the transitions that caused them
     */
    @Override
    public final Map<ClassifiedState, Collection<Transition>> getSuccessorsWithTransitions(ClassifiedState state) {
        if (cachedSuccessors.containsKey(state)) {
            return cachedSuccessors.get(state);
        }


        Map<State, Collection<Transition>> successors = animationLogic.getSuccessors(state);
        Map<ClassifiedState, Collection<Transition>> classifiedSuccessors = new HashMap<>();
        for (Map.Entry<State, Collection<Transition>> entry : successors.entrySet()) {
            ClassifiedState succ = classify(entry.getKey());
            if (!state.equals(succ)) {
                classifiedSuccessors.put(succ, entry.getValue());
            }
        }

        cachedSuccessors.put(state, classifiedSuccessors);
        return classifiedSuccessors;
    }

    /**
     * @param state state in the Petri net to find successors of
     * @return the successors of this state
     */
    @Override
    public final Collection<ClassifiedState> getSuccessors(ClassifiedState state) {
        return getSuccessorsWithTransitions(state).keySet();
    }

    /**
     * @param state
     * @param successor
     * @return the rate at which the state transitions to the successor in the underlying Petri net
     * @throws InvalidRateException
     */
    @Override
    public final double rate(ClassifiedState state, ClassifiedState successor) throws InvalidRateException {
        Collection<Transition> transitionsToSuccessor = getTransitions(state, successor);
        return getWeightOfTransitions(state, transitionsToSuccessor);
    }
    
    /**
     * @param state
     * @param successor
     * @return the names of the transitions that can be fired from this state
     */
    public final Collection<String> transitionNames(ClassifiedState state, ClassifiedState successor) {
    	Collection<Transition> transitionsToSuccessor = getTransitions(state, successor);
    	Collection<String> names = new ArrayList<>();
    	
    	for(Transition transition : transitionsToSuccessor) {
    		names.add(transition.getId());
    	}
    	
    	return names;
    }


    /**
     * Creates a new state containing the token counts for the
     * current Petri net.
     *
     * @return current state of the Petri net
     */
    @Override
    public final ClassifiedState getCurrentState() {
        HashedStateBuilder builder = new HashedStateBuilder();
        for (Place place : petriNet.getPlaces()) {
            for (Token token : petriNet.getTokens()) {
                builder.placeWithToken(place.getId(), token.getId(), place.getTokenCount(token.getId()));
            }
        }

        State state = builder.build();
        boolean tanigble = isTangible(state);
        return tanigble ? HashedClassifiedState.tangibleState(state) : HashedClassifiedState.vanishingState(state);
    }

    /**
     * Classifies the state into tangible or vanishing
     *
     * @param state
     * @return classified state
     */
    public final ClassifiedState classify(State state) {
        boolean tanigble = isTangible(state);
        return tanigble ? HashedClassifiedState.tangibleState(state) : HashedClassifiedState.vanishingState(state);
    }

    /**
     * A tangible state is one in which:
     * a) Has no enabled transitions
     * b) Has entirely timed transitions leaving it
     *
     * @param state to test for tangibility
     * @return true if the current token count setting is tangible
     */
    private boolean isTangible(State state) {
        Set<Transition> enabledTransitions = animationLogic.getEnabledTransitions(state);
        boolean anyTimed = false;
        boolean anyImmediate = false;
        for (Transition transition : enabledTransitions) {
            if (transition.isTimed()) {
                anyTimed = true;
            } else {
                anyImmediate = true;
            }
        }
        return enabledTransitions.isEmpty() || (anyTimed && !anyImmediate);
    }

    /**
     * Calculates the set of transitions that will take you from one state to the successor.
     * <p/>
     * Uses the current underlying cached methods so that duplicate calls to this method
     * will not result in another computation
     *
     * @param state     initial state
     * @param successor successor state, must be directly reachable from the state
     * @return enabled transitions that take you from state to successor, if it is not directly reachable then
     * an empty Collection will be returned
     */
    @Override
    public final Collection<Transition> getTransitions(ClassifiedState state, ClassifiedState successor) {
        Map<ClassifiedState, Collection<Transition>> stateTransitions = getSuccessorsWithTransitions(state);

        if (stateTransitions.containsKey(successor)) {
            return stateTransitions.get(successor);
        }
        return new LinkedList<>();
    }


    /**
     * Sums up the weights of the transitions. Transitions may have functional rates
     *
     * @param state
     * @param transitions
     * @return summed up the weight of the transitions specified
     */
    @Override
    public final double getWeightOfTransitions(ClassifiedState state, Iterable<Transition> transitions)
            throws InvalidRateException {
        double weight = 0;

        StateEvalVisitor evalVisitor = new StateEvalVisitor(petriNet, state);
        PetriNetWeightParser parser = new PetriNetWeightParser(evalVisitor, petriNet);
        for (Transition transition : transitions) {
            FunctionalResults<Double> results = parser.evaluateExpression(transition.getRateExpr());
            if (!results.hasErrors()) {
                weight += results.getResult();
            } else {
                throw new InvalidRateException(
                        "Invalid functional expression observed for transition : " + transition.getId() + " "
                                + transition.getRateExpr());
            }
        }
        return weight;
    }

    /**
     * @param state
     * @return all enabled transitions for the specified state
     */
    @Override
    public final Collection<Transition> getAllEnabledTransitions(ClassifiedState state) {
        Collection<Transition> results = new LinkedList<>();
        for (Collection<Transition> transitions : getSuccessorsWithTransitions(state).values()) {
            results.addAll(transitions);
        }
        return results;
    }

    /**
     * Clears the cached successors and any caching that is done via the animationLogic class
     */
    @Override
    public final void clear() {
        cachedSuccessors.clear();
        animationLogic.clear();
    }


}
