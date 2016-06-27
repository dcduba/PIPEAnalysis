package pipe.steadystate.algorithm;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.ac.imperial.state.Record;
import uk.ac.imperial.utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ParallelSteadyStateSolverTest {
    @Mock
    SteadyStateBuilder builder;

    @Mock
    SteadyStateSolver jacobiSolver;
    @Mock
    SteadyStateSolver gsSolver;

    @Before
    public void setUp() {
        when(builder.buildGaussSeidel()).thenReturn(gsSolver);
        when(builder.buildJacobi(any(ExecutorService.class), anyInt())).thenReturn(jacobiSolver);
    }


    /**
     * Since the Jacobi method transforms a CTMC A into a DTMC Q
     * and then solves for (I-Q)^Tx^T = 0
     * we need to pick a matrix which once transformed is diagonally
     * dominant once subtracted from I.
     *
     * This can be the following Matrix:
     */
    @Test
    public void runsJacobiForDiagonallyDominantMatrix() {
        ParallelSteadyStateSolver solver = new ParallelSteadyStateSolver(8, builder);

        List<Record> records = new ArrayList<>();
        Map<Integer, Pair<Double, Collection<String>>> successors0 = new HashMap<>();
        successors0.put(1, new Pair<Double, Collection<String>>(1.0, new ArrayList<String>()));
        records.add(new Record(0, successors0));

        Map<Integer, Pair<Double, Collection<String>>> successors1 = new HashMap<>();
        successors1.put(0, new Pair<Double, Collection<String>>(1.0, new ArrayList<String>()));
        records.add(new Record(1, successors1));


        solver.solve(records);

        verify(builder).buildJacobi(any(ExecutorService.class), eq(8));
        verify(builder, never()).buildGaussSeidel();
        verify(jacobiSolver).solve(records);
    }

    /**
     * Since the Jacobi method transforms a CTMC A into a DTMC Q
     * and then solves for (I-Q)^Tx^T = 0
     * we need to pick a matrix which once transformed is diagonally
     * dominant once subtracted from I.
     * 
     * This can be the Matrix below.
     * 
     * In production, we return the first result we find.  For testing, we need to 
     * verify that both solvers are invoked.  
     */
    @Test
    public void runsComboForNonDiagonallyDominantMatrix()  {
        ParallelSteadyStateSolver solver = new ParallelSteadyStateSolver(8, builder);
        solver.waitForAllSolversForTesting(true); 

        List<Record> records = new ArrayList<>();
        Map<Integer, Pair<Double, Collection<String>>> successors0 = new HashMap<>();
        successors0.put(1, new Pair<Double, Collection<String>>(2.0, new ArrayList<String>()));
        records.add(new Record(0, successors0));

        Map<Integer, Pair<Double, Collection<String>>> successors1 = new HashMap<>();
        successors1.put(0, new Pair<Double, Collection<String>>(1.0, new ArrayList<String>()));
        records.add(new Record(1, successors1));


        solver.solve(records);

        verify(builder).buildGaussSeidel();
        verify(builder).buildJacobi(any(ExecutorService.class), eq(7));
        verify(jacobiSolver).solve(records);
        verify(gsSolver).solve(records);
    }


}