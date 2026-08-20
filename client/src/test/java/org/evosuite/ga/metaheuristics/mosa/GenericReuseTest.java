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
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.DummyChromosome;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.ga.stoppingconditions.MaxGenerationStoppingCondition;
import org.evosuite.utils.Randomness;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verifies that {@link MOSA} and {@link DynaMOSA} can be used with a chromosome type that has
 * nothing to do with EvoSuite's test-generation machinery ({@code TestChromosome},
 * {@code TestFitnessFunction}, the {@code Archive} singleton, {@code Properties.CRITERION}, ...) -
 * only the generic {@code ga} package abstractions ({@link org.evosuite.ga.Chromosome},
 * {@link FitnessFunction}, {@link ChromosomeFactory}) plus the two convenience-constructor
 * defaults ({@code InMemorySearchArchive}, {@code OffspringFilter.mutateOnly()} /
 * {@code StaticGoalManager}).
 * <p>
 * This mirrors, in miniature, how an external consumer such as the EvoMBT (which generates
 * tests from EFSM models, not Java code, using EvoSuite purely as a library of search algorithms)
 * would use these algorithms with its own chromosome/fitness types.
 */
public class GenericReuseTest {

    private static final int NUM_GENES = 5;

    /**
     * {@code DummyChromosome#mutate()} replaces a gene with a full-range random {@code int}
     * (rather than a small perturbation), so hitting exactly 0 relies on population-level
     * diversity rather than hill-climbing - i.e. this problem is "solved" by chance within the
     * initial population/random-immigrant sampling (both bounded to a small range by
     * {@link RandomDummyChromosomeFactory}), not by mutation refining an existing solution. A
     * fixed seed and a generous generation budget make that reliable instead of flaky.
     */
    @Before
    public void fixRandomnessAndBudget() {
        Randomness.setSeed(42);
        Properties.SEARCH_BUDGET = 200;
    }

    /**
     * A trivial many-objective problem over {@link DummyChromosome}: goal {@code i} is satisfied
     * once gene {@code i} equals zero.
     */
    private static class ZeroGeneGoal extends FitnessFunction<DummyChromosome> {

        private static final long serialVersionUID = 1L;

        private final int index;

        ZeroGeneGoal(int index) {
            this.index = index;
        }

        @Override
        public double getFitness(DummyChromosome individual) {
            // DummyChromosome's crossOver() does not preserve length, so defend against a gene
            // at this index no longer existing rather than assuming a fixed-length chromosome.
            double fitness = index < individual.getGenes().size()
                    ? Math.abs(individual.get(index)) : Double.MAX_VALUE;
            updateIndividual(individual, fitness);
            return fitness;
        }

        @Override
        public boolean isMaximizationFunction() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ZeroGeneGoal && ((ZeroGeneGoal) o).index == this.index;
        }

        @Override
        public int hashCode() {
            return Objects.hash(index);
        }

        @Override
        public String toString() {
            return "gene[" + index + "] == 0";
        }
    }

    private static class RandomDummyChromosomeFactory implements ChromosomeFactory<DummyChromosome> {

        private static final long serialVersionUID = 1L;

        @Override
        public DummyChromosome getChromosome() {
            int[] values = new int[NUM_GENES];
            for (int i = 0; i < NUM_GENES; i++) {
                values[i] = Randomness.nextInt(21) - 10; // in [-10, 10]
            }
            return new DummyChromosome(values);
        }
    }

    private static List<FitnessFunction<DummyChromosome>> goals() {
        List<FitnessFunction<DummyChromosome>> goals = new ArrayList<>();
        for (int i = 0; i < NUM_GENES; i++) {
            goals.add(new ZeroGeneGoal(i));
        }
        return goals;
    }

    @Test
    public void mosaCoversAllGoalsWithCustomChromosome() {
        Properties.POPULATION = 20;

        GeneticAlgorithm<DummyChromosome> mosa = new MOSA<>(new RandomDummyChromosomeFactory());
        mosa.addStoppingCondition(new MaxGenerationStoppingCondition<>());
        mosa.addFitnessFunctions(goals());

        mosa.generateSolution();
        
        DummyChromosome bestIndividual = mosa.getBestIndividual();

        assertNotNull(bestIndividual);
        
        MOSA<DummyChromosome> m = (MOSA<DummyChromosome>)mosa;
        assertEquals(NUM_GENES, m.getNumberOfCoveredGoals());
        assertEquals(0, m.getNumberOfUncoveredGoals());
    }

    @Test
    public void dynaMosaCoversAllGoalsWithCustomChromosome() {
        Properties.POPULATION = 20;

        GeneticAlgorithm<DummyChromosome> dynaMosa = new DynaMOSA<>(new RandomDummyChromosomeFactory());
        dynaMosa.addStoppingCondition(new MaxGenerationStoppingCondition<>());
        dynaMosa.addFitnessFunctions(goals());

        dynaMosa.generateSolution();

        DummyChromosome bestIndividual = dynaMosa.getBestIndividual();
        assertNotNull(bestIndividual);
        
        DynaMOSA<DummyChromosome> dm = (DynaMOSA<DummyChromosome>)dynaMosa;
        assertEquals(NUM_GENES, dm.getNumberOfCoveredGoals());
        assertEquals(0, dm.getNumberOfUncoveredGoals());
    }
}
