package pipe.steadystate.metrics;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import pipe.reachability.TangibleOnlyUtils;
import pipe.reachability.algorithm.TimelessTrapException;
import pipe.reachability.algorithm.UnboundedExplorerUtilities;
import pipe.steadystate.algorithm.SteadyStateBuilder;
import pipe.steadystate.algorithm.SteadyStateBuilderImpl;
import pipe.steadystate.algorithm.SteadyStateSolver;
import uk.ac.imperial.pipe.exceptions.InvalidRateException;
import uk.ac.imperial.pipe.models.petrinet.PetriNet;
import uk.ac.imperial.pipe.parsers.UnparsableException;
import uk.ac.imperial.state.ClassifiedState;
import uk.ac.imperial.state.Record;
import utils.Utils;

import javax.xml.bind.JAXBException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

public class MetricsStepDefinitions {

    /**
     * Number of threads to run parallel solvers with
     */
    private static final int THREADS = 4;

    private PetriNet petriNet;

    /**
     * Steady state probabilities
     */
    private Map<Integer, Double> probabilities = new HashMap<>();

    private Map<Integer, ClassifiedState> stateMappings = new HashMap<>();


    @Given("^I use the Petri net located at (/[\\w/]+.xml)$")
    public void I_use_the_Petri_net_located_at(String path) throws JAXBException, UnparsableException, FileNotFoundException {
        petriNet = Utils.readPetriNet(path);
    }

    @When("^I calculate the metrics")
    public void I_calculate_the_metrics()
            throws InterruptedException, ExecutionException, IOException, InvalidRateException, TimelessTrapException {
        TangibleOnlyUtils utils = new TangibleOnlyUtils();
        Utils.StateSpaceResult result = Utils.performStateSpaceExplore(utils, new UnboundedExplorerUtilities(petriNet));
        List<Record> records = new ArrayList<>(result.results);
        stateMappings.putAll(result.states);
        SteadyStateBuilder builder = new SteadyStateBuilderImpl();
        SteadyStateSolver solver = builder.buildGaussSeidel();

        probabilities.putAll(solver.solve(records));

    }

    @Then("^I expect the places to have the following average number of tokens")
    public void I_expect_the_places_to_have_the_following_average_number_of_tokens(String jsonValue)
            throws IOException {
        Map<String, Map<String, Double>> averageTokens =
                TokenMetrics.averageTokensOnPlace(stateMappings, probabilities);
        Map<String, Map<String, Double>> expectedTokens = jsonToMap(jsonValue);
        assertEqualityDoubleMap(expectedTokens, averageTokens);

    }

    /**
     * @return
     * @throws IOException
     */
    private <T> T jsonToMap(String jsonObject) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonObject, new TypeReference<T>() {
        });
    }

    private void assertEqualityDoubleMap(Map<String, Map<String, Double>> expected,
                                         Map<String, Map<String, Double>> actual) {
        assertEquals("Place keys are not equal", expected.keySet(), actual.keySet());
        for (Map.Entry<String, Map<String, Double>> entry : expected.entrySet()) {
            Map<String, Double> actualMap = actual.get(entry.getKey());
            Map<String, Double> expectedMap = entry.getValue();
            assertEquality(expectedMap, actualMap);

        }
    }

    private void assertEquality(Map<String, Double> expected, Map<String, Double> actual) {
        assertEquals("Token keys are not equal", expected.keySet(), actual.keySet());
        for (Map.Entry<String, Double> entry : expected.entrySet()) {

            assertEquals("Values for " + entry.getKey() + " are not equal ", entry.getValue(),
                    actual.get(entry.getKey()), 0.0001);

        }
    }

    @Then("^I expect the transition through" +
            "puts to be")
    public void I_expect_the_transition_throughputs_to_be(String jsonValue) throws IOException {
        Map<String, Double> actual = TransitionMetrics.getTransitionThroughput(stateMappings, probabilities, petriNet);
        Map<String, Double> expected = jsonToMap(jsonValue);
        assertEquality(expected, actual);

    }


}
