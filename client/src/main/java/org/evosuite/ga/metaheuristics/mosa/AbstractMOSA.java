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
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.archive.SearchArchive;
import org.evosuite.ga.comparators.DominanceComparator;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.utils.BudgetConsumptionMonitor;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Abstract class for MOSA or variants of MOSA (many-objective sorting algorithms).
 * <p>
 * This class is chromosome-agnostic: it depends only on the generic {@code ga} package
 * abstractions ({@link Chromosome}, {@link FitnessFunction}, the ranking/crowding-distance
 * operators) plus two small injectable collaborators - a {@link SearchArchive} (the best-known
 * solution(s) per target) and an {@link OffspringFilter} (domain-specific offspring mutation and
 * refinement) - so it can be reused with any {@code Chromosome<T>}/{@code FitnessFunction<T>}
 * pair, not just EvoSuite's own {@code TestChromosome}/{@code TestFitnessFunction}. EvoSuite's own
 * {@code MOSA}/{@code DynaMOSA} instances are wired up (in
 * {@code org.evosuite.strategy.PropertiesSuiteGAFactory}) with an {@link SearchArchive} that
 * delegates to EvoSuite's process-wide {@code Archive} singleton and an {@link OffspringFilter}
 * that replicates the original TestChromosome-specific breeding refinements, so their behavior is
 * unchanged.
 *
 * @param <T> the chromosome type being evolved
 * @author Annibale Panichella, Fitsum M. Kifetew
 */
public abstract class AbstractMOSA<T extends Chromosome<T>> extends GeneticAlgorithm<T> {

    private static final long serialVersionUID = 146182080947267628L;

    private static final Logger logger = LoggerFactory.getLogger(AbstractMOSA.class);

    /**
     * Best-known solution(s) per target.
     */
    protected final SearchArchive<T> archive;

    /**
     * Domain-specific offspring mutation/refinement, applied after crossover and before fitness
     * evaluation.
     */
    protected final OffspringFilter<T> offspringFilter;

    /**
     * Object used to keep track of the execution time needed to reach the maximum coverage.
     */
    protected final BudgetConsumptionMonitor budgetMonitor;

    /**
     * Extra, domain-specific post-processing run after a chromosome's fitness has been computed
     * (e.g. EvoSuite derives exception-coverage goals from the chromosome's execution result
     * here). No-op by default.
     */
    protected final Consumer<T> onFitnessCalculated;

    /**
     * Constructor.
     *
     * @param factory         a {@link ChromosomeFactory} object
     * @param archive         the best-known-solution(s)-per-target archive to use
     * @param offspringFilter domain-specific offspring mutation/refinement to apply during
     *                        breeding
     */
    protected AbstractMOSA(ChromosomeFactory<T> factory, SearchArchive<T> archive,
                            OffspringFilter<T> offspringFilter) {
        this(factory, archive, offspringFilter, c -> {
        });
    }

    /**
     * Constructor.
     *
     * @param factory             a {@link ChromosomeFactory} object
     * @param archive             the best-known-solution(s)-per-target archive to use
     * @param offspringFilter     domain-specific offspring mutation/refinement to apply during
     *                            breeding
     * @param onFitnessCalculated extra post-processing to run after a chromosome's fitness has
     *                            been computed
     */
    protected AbstractMOSA(ChromosomeFactory<T> factory, SearchArchive<T> archive,
                            OffspringFilter<T> offspringFilter, Consumer<T> onFitnessCalculated) {
        super(factory);
        this.archive = Objects.requireNonNull(archive);
        this.offspringFilter = Objects.requireNonNull(offspringFilter);
        this.onFitnessCalculated = Objects.requireNonNull(onFitnessCalculated);
        this.budgetMonitor = new BudgetConsumptionMonitor();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overridden (rather than relying on {@link GeneticAlgorithm}'s default) to avoid also
     * registering {@code function} with the local-search objective, which MOSA/DynaMOSA do not
     * use.
     */
    @Override
    public void addFitnessFunction(FitnessFunction<T> function) {
        this.fitnessFunctions.add(function);
    }

    /**
     * This method is used to generate new individuals (offspring) from
     * the current population. The offspring population has the same size as the parent population.
     *
     * @return offspring population
     */
    protected List<T> breedNextGeneration() {
        List<T> offspringPopulation = new ArrayList<>(Properties.POPULATION);
        // we apply only Properties.POPULATION/2 iterations since in each generation
        // we generate two offsprings
        for (int i = 0; i < Properties.POPULATION / 2 && !this.isFinished(); i++) {
            // select best individuals

            /*
             * the same individual could be selected twice! Is this a problem for crossover?
             * Because crossing over an individual with itself will most certainly give you the
             * same individual again...
             */

            T parent1 = this.selectionFunction.select(this.population);
            T parent2 = this.selectionFunction.select(this.population);
            T offspring1 = parent1.clone();
            T offspring2 = parent2.clone();
            // apply crossover
            if (Randomness.nextDouble() <= Properties.CROSSOVER_RATE) {
                try {
                    this.crossoverFunction.crossOver(offspring1, offspring2);
                } catch (ConstructionFailedException e) {
                    logger.debug("CrossOver failed.");
                    continue;
                }
            }

            this.prepareAndAdd(offspring1, parent1, offspringPopulation);
            this.prepareAndAdd(offspring2, parent2, offspringPopulation);
        }
        // Add new randomly generate tests
        for (int i = 0; i < Properties.POPULATION * Properties.P_TEST_INSERTION; i++) {
            final T tch;
            if (this.getCoveredGoals().isEmpty() || Randomness.nextBoolean()) {
                tch = this.chromosomeFactory.getChromosome();
                tch.setChanged(true);
            } else {
                tch = Randomness.choice(this.getSolutions()).clone();
                tch.mutate();
            }
            if (tch.isChanged()) {
                tch.updateAge(this.currentIteration);
                this.calculateFitness(tch);
                offspringPopulation.add(tch);
            }
        }
        logger.info("Number of offsprings = {}", offspringPopulation.size());
        return offspringPopulation;
    }

    /**
     * Applies {@link #offspringFilter} to a freshly crossed-over offspring, and - if it is still
     * present and changed - evaluates its fitness and adds it to {@code offspringPopulation}.
     */
    private void prepareAndAdd(T offspring, T parent, List<T> offspringPopulation) {
        T prepared = this.offspringFilter.prepareOffspring(offspring, parent);
        this.notifyMutation(offspring);
        if (prepared != null && prepared.isChanged()) {
            prepared.getFitnessValues().clear();
            prepared.updateAge(this.currentIteration);
            this.calculateFitness(prepared);
            offspringPopulation.add(prepared);
        }
    }

    /**
     * This method extracts non-dominated solutions (tests) according to all covered goal
     * (e.g., branches).
     *
     * @param solutions list of test cases to analyze with the "dominance" relationship
     * @return the non-dominated set of test cases
     */
    public List<T> getNonDominatedSolutions(List<T> solutions) {
        final DominanceComparator<T> comparator = new DominanceComparator<>(this.getCoveredGoals());
        final List<T> nextFront = new ArrayList<>(solutions.size());
        boolean isDominated;
        for (T p : solutions) {
            isDominated = false;
            List<T> dominatedSolutions = new ArrayList<>(solutions.size());
            for (T best : nextFront) {
                final int flag = comparator.compare(p, best);
                if (flag < 0) {
                    dominatedSolutions.add(best);
                }
                if (flag > 0) {
                    isDominated = true;
                }
            }
            if (isDominated) {
                continue;
            }

            nextFront.add(p);
            nextFront.removeAll(dominatedSolutions);
        }
        return nextFront;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializePopulation() {
        logger.info("executing initializePopulation function");

        this.notifySearchStarted();
        this.currentIteration = 0;

        // Create a random parent population P0
        this.generateInitialPopulation(Properties.POPULATION);

        // Determine fitness
        this.calculateFitness();
        this.notifyIteration();
    }

    /**
     * Returns the goals that have been covered by the test cases stored in the archive.
     *
     * @return
     */
    protected Set<FitnessFunction<T>> getCoveredGoals() {
        return new LinkedHashSet<>(archive.getCoveredTargets());
    }

    /**
     * Returns the number of goals that have been covered by the test cases stored in the archive.
     *
     * @return
     */
    protected int getNumberOfCoveredGoals() {
        return archive.getNumberOfCoveredTargets();
    }

    protected void addUncoveredGoal(FitnessFunction<T> goal) {
        archive.addTarget(goal);
    }

    /**
     * Returns the goals that have not been covered by the test cases stored in the archive.
     *
     * @return
     */
    protected Set<FitnessFunction<T>> getUncoveredGoals() {
        return new LinkedHashSet<>(archive.getUncoveredTargets());
    }

    /**
     * Returns the goals that have not been covered by the test cases stored in the archive.
     *
     * @return
     */
    protected int getNumberOfUncoveredGoals() {
        return archive.getNumberOfUncoveredTargets();
    }

    /**
     * Returns the total number of goals, i.e., number of covered goals + number of uncovered goals.
     *
     * @return
     */
    protected int getTotalNumberOfGoals() {
        return archive.getNumberOfTargets();
    }

    /**
     * Return the test cases in the archive as a list.
     *
     * @return
     */
    protected List<T> getSolutions() {
        return new ArrayList<>(archive.getSolutions());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void calculateFitness(T c) {
        for (FitnessFunction<T> fitnessFunction : this.fitnessFunctions) {
            double fitness = fitnessFunction.getFitness(c);
            // Update the archive ourselves rather than assuming the fitness function does so as a
            // side effect (EvoSuite's own TestFitnessFunction implementations do, via the Archive
            // singleton, but a generic FitnessFunction<T> has no reason to know about any
            // archive at all).
            boolean covered = fitnessFunction.isMaximizationFunction() ? fitness >= 1.0 : fitness == 0.0;
            if (covered) {
                this.archive.updateArchive(fitnessFunction, c, fitness);
            }
        }
        this.onFitnessCalculated.accept(c);
        this.notifyEvaluation(c);
        // update the time needed to reach the max coverage
        this.budgetMonitor.checkMaxCoverage(this.getNumberOfCoveredGoals());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<T> getBestIndividuals() {
        return this.getNonDominatedSolutions(this.population);
    }
}
