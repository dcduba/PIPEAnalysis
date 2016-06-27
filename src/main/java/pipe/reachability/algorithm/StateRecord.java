package pipe.reachability.algorithm;

import java.util.Collection;
import java.util.ArrayList;

import uk.ac.imperial.state.ClassifiedState;
import uk.ac.imperial.utils.Pair;

/**
 * Record containing a state and the rate and transition names into it.
 * Used for the state space exploration algorithm to perform on the fly
 * elimination of vanishing states.
 */
public final class StateRecord {

    /**
     * State
     */
    private final ClassifiedState state;

    /**
     * Rate into the state
     */
    private double rate;
    
    /**
     * Transition names resulting in this state
     */
    private Collection<String> transitionNames = new ArrayList<>();
    
    /**
     * Constructor that sets the state and rate for the record
     * @param state
     * @param rate
     */
    public StateRecord(ClassifiedState state, double rate, Collection<String> transitionNames) {
        this.state = state;
        this.rate = rate;
        this.transitionNames.addAll(transitionNames);
    }
    
    /**
     * 
     * @return transition names resulting in this state
     */
    public Collection<String> getNames() {
    	return transitionNames;
    }
    
    public void addName(String name) {
    	if(!transitionNames.contains(name)) {
    		transitionNames.add(name);
    	}
    }

    /**
     *
     * @return rate into the vanishing state
     */
    public double getRate() {
        return rate;
    }
    
    /**
     * Shorthand to get both the rate and the names
     * 
     * @return both the rate and the transition names
     */
    public Pair<Double, Collection<String>> getPair() {
    	return new Pair<>(getRate(), getNames());
    }

    /**
     *
     * @return vanishing state rate
     */
    public ClassifiedState getState() {
        return state;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }
}
