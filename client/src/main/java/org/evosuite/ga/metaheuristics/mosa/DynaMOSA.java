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

import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.archive.InMemorySearchArchive;
import org.evosuite.ga.archive.SearchArchive;
import org.evosuite.ga.comparators.OnlyCrowdingComparator;
import org.evosuite.ga.metaheuristics.mosa.structural.GoalManager;
import org.evosuite.ga.metaheuristics.mosa.structural.GoalManagerFactory;
import org.evosuite.ga.metaheuristics.mosa.structural.StaticGoalManager;
import org.evosuite.ga.operators.ranking.CrowdingDistance;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation of the DynaMOSA (Many Objective Sorting Algorithm) described in the paper
 * "Automated Test Case Generation as a Many-Objective Optimisation Problem with Dynamic Selection
 * of the Targets".
 * <p>
 * Chromosome-agnostic - see {@link AbstractMOSA} - so it can be used with any
 * {@code Chromosome<T>}/{@code FitnessFunction<T>} pair. DynaMOSA's defining feature - narrowing
 * the search to a dynamically expanding frontier of "current goals" based on dependencies between
 * targets - is delegated to an injectable {@link GoalManager}, produced (once the targets are
 * known) by a {@link GoalManagerFactory}. EvoSuite's own instances use a
 * {@code MultiCriteriaManager}, deriving that dependency relation from Java bytecode
 * branch/control-dependency information; a consumer without an equivalent dependency relation for
 * its own domain can use {@link StaticGoalManager} (all goals active from the start, i.e. plain
 * MOSA behavior) as a default.
 *
 * @param <T> the chromosome type being evolved
 * @author Annibale Panichella, Fitsum M. Kifetew, Paolo Tonella
 */
public class DynaMOSA<T extends Chromosome<T>> extends AbstractMOSA<T> {

    private static final long serialVersionUID = 146182080947267628L;

    private static final Logger logger = LoggerFactory.getLogger(DynaMOSA.class);

    private final GoalManagerFactory<T> goalManagerFactory;

    /**
     * Manager to determine the test goals to consider at each generation
     */
    protected GoalManager<T> goalsManager = null;

    protected CrowdingDistance<T> distance = new CrowdingDistance<>();

    /**
     * Convenience constructor for reuse outside of EvoSuite's own test-generation pipeline: uses
     * a simple in-memory {@link SearchArchive} and a {@link StaticGoalManager} (i.e. plain,
     * non-dynamic MOSA behavior, since no dependency relation between targets is assumed) and
     * performs no offspring refinement beyond mutation.
     *
     * @param factory a {@link ChromosomeFactory} object
     */
    public DynaMOSA(ChromosomeFactory<T> factory) {
        this(factory, new InMemorySearchArchive<>(), OffspringFilter.mutateOnly());
    }

    /**
     * @param factory         a {@link ChromosomeFactory} object
     * @param archive         the best-known-solution(s)-per-target archive to use
     * @param offspringFilter domain-specific offspring mutation/refinement to apply during
     *                        breeding
     */
    public DynaMOSA(ChromosomeFactory<T> factory, SearchArchive<T> archive, OffspringFilter<T> offspringFilter) {
        this(factory, archive, offspringFilter, targets -> new StaticGoalManager<>(archive, targets));
    }

    /**
     * @param factory            a {@link ChromosomeFactory} object
     * @param archive            the best-known-solution(s)-per-target archive to use
     * @param offspringFilter    domain-specific offspring mutation/refinement to apply during
     *                           breeding
     * @param goalManagerFactory creates the {@link GoalManager} driving dynamic target selection,
     *                           once the targets are known
     */
    public DynaMOSA(ChromosomeFactory<T> factory, SearchArchive<T> archive, OffspringFilter<T> offspringFilter,
                     GoalManagerFactory<T> goalManagerFactory) {
        super(factory, archive, offspringFilter);
        this.goalManagerFactory = goalManagerFactory;
    }

    /**
     * @param factory             a {@link ChromosomeFactory} object
     * @param archive             the best-known-solution(s)-per-target archive to use
     * @param offspringFilter     domain-specific offspring mutation/refinement to apply during
     *                            breeding
     * @param goalManagerFactory  creates the {@link GoalManager} driving dynamic target
     *                            selection, once the targets are known
     * @param onFitnessCalculated extra post-processing to run after a chromosome's fitness has
     *                            been computed
     */
    public DynaMOSA(ChromosomeFactory<T> factory, SearchArchive<T> archive, OffspringFilter<T> offspringFilter,
                     GoalManagerFactory<T> goalManagerFactory, Consumer<T> onFitnessCalculated) {
        super(factory, archive, offspringFilter, onFitnessCalculated);
        this.goalManagerFactory = goalManagerFactory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void evolve() {
        // Generate offspring, compute their fitness, update the archive and coverage goals.
        List<T> offspringPopulation = this.breedNextGeneration();

        // Create the union of parents and offspring
        List<T> union = new ArrayList<>(this.population.size() + offspringPopulation.size());
        union.addAll(this.population);
        union.addAll(offspringPopulation);

        // Ranking the union
        logger.debug("Union Size = {}", union.size());

        // Ranking the union using the best rank algorithm (modified version of the non dominated
        // sorting algorithm)
        this.rankingFunction.computeRankingAssignment(union, this.goalsManager.getCurrentGoals());

        // let's form the next population using "preference sorting and non-dominated sorting" on the
        // updated set of goals
        int remain = Math.max(Properties.POPULATION, this.rankingFunction.getSubfront(0).size());
        int index = 0;
        this.population.clear();

        // Obtain the first front
        List<T> front = this.rankingFunction.getSubfront(index);

        // Successively iterate through the fronts (starting with the first non-dominated front)
        // and insert their members into the population for the next generation. This is done until
        // all fronts have been processed or we hit a front that is too big to fit into the next
        // population as a whole.
        while ((remain > 0) && (remain >= front.size()) && !front.isEmpty()) {
            // Assign crowding distance to individuals
            this.distance.fastEpsilonDominanceAssignment(front, this.goalsManager.getCurrentGoals());

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

        // In case the population for the next generation has not been filled up completely yet,
        // we insert the best individuals from the current front (the one that was too big to fit
        // entirely) until there are no more free places left. To this end, and in an effort to
        // promote diversity, we consider those individuals with a higher crowding distance as
        // being better.
        if (remain > 0 && !front.isEmpty()) { // front contains individuals to insert
            this.distance.fastEpsilonDominanceAssignment(front, this.goalsManager.getCurrentGoals());
            front.sort(new OnlyCrowdingComparator<>());
            for (int k = 0; k < remain; k++) {
                this.population.add(front.get(k));
            }
        }

        this.currentIteration++;
        //logger.debug("N. fronts = {}", ranking.getNumberOfSubfronts());
        //logger.debug("1* front size = {}", ranking.getSubfront(0).size());
        logger.debug("Covered goals = {}", goalsManager.getCoveredGoals().size());
        logger.debug("Current goals = {}", goalsManager.getCurrentGoals().size());
        logger.debug("Uncovered goals = {}", goalsManager.getUncoveredGoals().size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void generateSolution() {
        logger.debug("executing generateSolution function");

        // Set up the targets to cover, which are initially free of any control dependencies.
        // We are trying to optimize for multiple targets at the same time.
        this.goalsManager = this.goalManagerFactory.create(this.fitnessFunctions);

        LoggingUtils.getEvoLogger().info("* Initial Number of Goals in DynaMOSA = " +
                this.goalsManager.getCurrentGoals().size() + " / " + this.getUncoveredGoals().size());

        logger.debug("Initial Number of Goals = " + this.goalsManager.getCurrentGoals().size());

        if (this.population.isEmpty()) {
            // Initialize the population by creating solutions at random.
            this.initializePopulation();
        }

        // Compute the fitness for each population member, update the coverage information and the
        // set of goals to cover. Finally, update the archive.
        // this.calculateFitness(); // Not required, already done by this.initializePopulation();

        // Calculate dominance ranks and crowding distance. This is required to decide which
        // individuals should be used for mutation and crossover in the first iteration of the main
        // search loop.
        this.rankingFunction.computeRankingAssignment(this.population, this.goalsManager.getCurrentGoals());
        for (int i = 0; i < this.rankingFunction.getNumberOfSubfronts(); i++) {
            this.distance.fastEpsilonDominanceAssignment(this.rankingFunction.getSubfront(i), this.goalsManager.getCurrentGoals());
        }

        // Evolve the population generation by generation until all gaols have been covered or the
        // search budget has been consumed.
        while (!isFinished() && this.goalsManager.getUncoveredGoals().size() > 0) {
            this.evolve();
            this.notifyIteration();
        }

        this.notifySearchFinished();
    }

    /**
     * Calculates the fitness for the given individual. Also updates the list of targets to cover,
     * as well as the population of best solutions in the archive.
     *
     * @param c the chromosome whose fitness to compute
     */
    @Override
    protected void calculateFitness(T c) {
        if (!isFinished()) {
            // this also updates the archive and the targets
            this.goalsManager.calculateFitness(c, this);
            this.notifyEvaluation(c);
        }
    }

    @Override
    public List<? extends FitnessFunction<T>> getFitnessFunctions() {
        List<FitnessFunction<T>> allGoals = new ArrayList<>(goalsManager.getCoveredGoals());
        allGoals.addAll(goalsManager.getUncoveredGoals());
        return allGoals;
    }
}
