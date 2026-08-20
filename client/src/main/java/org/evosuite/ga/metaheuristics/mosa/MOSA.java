/*
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.ga.metaheuristics.mosa;

import org.evosuite.ClientProcess;
import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.archive.InMemorySearchArchive;
import org.evosuite.ga.archive.SearchArchive;
import org.evosuite.ga.comparators.OnlyCrowdingComparator;
import org.evosuite.ga.operators.ranking.CrowdingDistance;
import org.evosuite.ga.operators.selection.BestKSelection;
import org.evosuite.ga.operators.selection.RandomKSelection;
import org.evosuite.ga.operators.selection.RankSelection;
import org.evosuite.ga.operators.selection.SelectionFunction;
import org.evosuite.rmi.ClientServices;
import org.evosuite.rmi.service.ClientNodeLocal;
import org.evosuite.statistics.RuntimeVariable;
import org.evosuite.utils.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Implementation of the Many-Objective Sorting Algorithm (MOSA) described in the paper
 * "Reformulating branch coverage as a many-objective optimization problem".
 * <p>
 * Chromosome-agnostic - see {@link AbstractMOSA} - so it can be used with any
 * {@code Chromosome<T>}/{@code FitnessFunction<T>} pair, not just EvoSuite's own test-generation
 * types.
 *
 * @param <T> the chromosome type being evolved
 * @author Annibale Panichella, Fitsum M. Kifetew
 */
public class MOSA<T extends Chromosome<T>> extends AbstractMOSA<T> {

    private static final long serialVersionUID = 146182080947267628L;

    private static final Logger logger = LoggerFactory.getLogger(MOSA.class);

    /**
     * immigrant groups from neighbouring client
     */
    private final ConcurrentLinkedQueue<List<T>> immigrants = new ConcurrentLinkedQueue<>();

    private final SelectionFunction<T> emigrantsSelection;

    /**
     * Crowding distance measure to use
     */
    protected CrowdingDistance<T> distance = new CrowdingDistance<>();

    /**
     * Convenience constructor for reuse outside of EvoSuite's own test-generation pipeline: uses
     * a simple in-memory {@link SearchArchive} and performs no offspring refinement beyond
     * mutation.
     *
     * @param factory a {@link ChromosomeFactory} object
     */
    public MOSA(ChromosomeFactory<T> factory) {
        this(factory, new InMemorySearchArchive<>(), OffspringFilter.mutateOnly());
    }

    /**
     * @param factory         a {@link ChromosomeFactory} object
     * @param archive         the best-known-solution(s)-per-target archive to use
     * @param offspringFilter domain-specific offspring mutation/refinement to apply during
     *                        breeding
     */
    public MOSA(ChromosomeFactory<T> factory, SearchArchive<T> archive, OffspringFilter<T> offspringFilter) {
        super(factory, archive, offspringFilter);
        this.emigrantsSelection = createEmigrantsSelection();
    }

    /**
     * @param factory             a {@link ChromosomeFactory} object
     * @param archive             the best-known-solution(s)-per-target archive to use
     * @param offspringFilter     domain-specific offspring mutation/refinement to apply during
     *                            breeding
     * @param onFitnessCalculated extra post-processing to run after a chromosome's fitness has
     *                            been computed
     */
    public MOSA(ChromosomeFactory<T> factory, SearchArchive<T> archive, OffspringFilter<T> offspringFilter,
                Consumer<T> onFitnessCalculated) {
        super(factory, archive, offspringFilter, onFitnessCalculated);
        this.emigrantsSelection = createEmigrantsSelection();
    }

    private static <T extends Chromosome<T>> SelectionFunction<T> createEmigrantsSelection() {
        switch (Properties.EMIGRANT_SELECTION_FUNCTION) {
            case RANK:
                return new RankSelection<>();
            case RANDOMK:
                return new RandomKSelection<>();
            default:
                return new BestKSelection<>();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void evolve() {
        List<T> offspringPopulation = this.breedNextGeneration();

        // Create the union of parents and offSpring
        List<T> union = new ArrayList<>();
        union.addAll(this.population);
        union.addAll(offspringPopulation);

        // for parallel runs: integrate possible immigrants
        if (Properties.NUM_PARALLEL_CLIENTS > 1 && !immigrants.isEmpty()) {
            union.addAll(immigrants.poll());
        }

        Set<FitnessFunction<T>> uncoveredGoals = this.getUncoveredGoals();

        // Ranking the union
        logger.debug("Union Size =" + union.size());
        // Ranking the union using the best rank algorithm (modified version of the non dominated sorting algorithm)
        this.rankingFunction.computeRankingAssignment(union, uncoveredGoals);

        int remain = this.population.size();
        int index = 0;
        List<T> front;
        this.population.clear();

        // Obtain the next front
        front = this.rankingFunction.getSubfront(index);

        while ((remain > 0) && (remain >= front.size()) && !front.isEmpty()) {
            // Assign crowding distance to individuals
            this.distance.fastEpsilonDominanceAssignment(front, uncoveredGoals);
            // Add the individuals of this front
            this.population.addAll(front);

            // Decrement remain
            remain = remain - front.size();

            // Obtain the next front
            index++;
            if (remain > 0) {
                front = this.rankingFunction.getSubfront(index);
            }
        }

        // Remain is less than front(index).size, insert only the best one
        if (remain > 0 && !front.isEmpty()) { // front contains individuals to insert
            this.distance.fastEpsilonDominanceAssignment(front, uncoveredGoals);
            front.sort(new OnlyCrowdingComparator<>());
            for (int k = 0; k < remain; k++) {
                this.population.add(front.get(k));
            }
        }

        // for parallel runs: collect best k individuals for migration
        if (Properties.NUM_PARALLEL_CLIENTS > 1 && Properties.MIGRANTS_ITERATION_FREQUENCY > 0) {
            if ((currentIteration + 1) % Properties.MIGRANTS_ITERATION_FREQUENCY == 0 && !this.population.isEmpty()) {
                HashSet<T> emigrants = new HashSet<>(emigrantsSelection.select(this.population,
                        Properties.MIGRANTS_COMMUNICATION_RATE));
                ClientServices.<T>getInstance().getClientNode().emigrate(emigrants);
            }
        }

        this.currentIteration++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void generateSolution() {
        logger.info("executing generateSolution function");

        // keep track of covered goals
        this.fitnessFunctions.forEach(this::addUncoveredGoal);

        // initialize population
        if (this.population.isEmpty()) {
            this.initializePopulation();
        }

        // Calculate dominance ranks and crowding distance
        this.rankingFunction.computeRankingAssignment(this.population, this.getUncoveredGoals());
        for (int i = 0; i < this.rankingFunction.getNumberOfSubfronts(); i++) {
            this.distance.fastEpsilonDominanceAssignment(this.rankingFunction.getSubfront(i), this.getUncoveredGoals());
        }

        final ClientNodeLocal<T> clientNode = ClientServices.<T>getInstance().getClientNode();

        Listener<Set<T>> listener = null;
        if (Properties.NUM_PARALLEL_CLIENTS > 1) {
            listener = event -> immigrants.add(new LinkedList<>(event));
            clientNode.addListener(listener);
        }

        // TODO add here dynamic stopping condition
        while (!this.isFinished() && this.getNumberOfUncoveredGoals() > 0) {
            this.evolve();
            this.notifyIteration();
        }

        if (Properties.NUM_PARALLEL_CLIENTS > 1) {
            clientNode.deleteListener(listener);

            if (ClientProcess.DEFAULT_CLIENT_NAME.equals(ClientProcess.getIdentifier())) {
                //collect all end result test cases
                Set<Set<T>> collectedSolutions = clientNode.getBestSolutions();

                logger.debug(ClientProcess.DEFAULT_CLIENT_NAME + ": Received " + collectedSolutions.size() + " solution sets");
                for (Set<T> solution : collectedSolutions) {
                    for (T t : solution) {
                        this.calculateFitness(t);
                    }
                }
            } else {
                //send end result test cases to Client-0
                Set<T> solutionsSet = new HashSet<>(getSolutions());
                logger.debug(ClientProcess.getPrettyPrintIdentifier() + "Sending " + solutionsSet.size()
                        + " solutions to " + ClientProcess.DEFAULT_CLIENT_NAME);
                clientNode.sendBestSolution(solutionsSet);
            }
        }

        // storing the time needed to reach the maximum coverage
        clientNode.trackOutputVariable(RuntimeVariable.Time2MaxCoverage,
                this.budgetMonitor.getTime2MaxCoverage());
        this.notifySearchFinished();
    }
}
