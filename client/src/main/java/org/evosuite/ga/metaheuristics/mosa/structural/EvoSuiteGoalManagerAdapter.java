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
package org.evosuite.ga.metaheuristics.mosa.structural;

import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridges the generic {@link GoalManager} interface onto EvoSuite's own
 * {@link MultiCriteriaManager} (branch/control-dependency-driven dynamic target selection over
 * {@code TestChromosome}s), so that {@code DynaMOSA}'s exact existing behavior is preserved when
 * used within EvoSuite's own test-generation pipeline. A consumer reusing DynaMOSA outside of
 * EvoSuite supplies its own {@link GoalManager} instead (see {@link StaticGoalManager} for a
 * simple default).
 */
public class EvoSuiteGoalManagerAdapter implements GoalManager<TestChromosome> {

    private static final long serialVersionUID = 1L;

    private final MultiCriteriaManager delegate;

    public EvoSuiteGoalManagerAdapter(List<? extends FitnessFunction<TestChromosome>> targets) {
        List<TestFitnessFunction> testFitnessFunctions = targets.stream()
                .map(target -> {
                    if (!(target instanceof TestFitnessFunction)) {
                        throw new IllegalArgumentException(
                                "Only TestFitnessFunctions are supported, but got: "
                                        + target.getClass().getCanonicalName());
                    }
                    return (TestFitnessFunction) target;
                })
                .collect(Collectors.toList());
        this.delegate = new MultiCriteriaManager(testFitnessFunctions);
    }

    @Override
    public void calculateFitness(TestChromosome c, GeneticAlgorithm<TestChromosome> ga) {
        delegate.calculateFitness(c, ga);
    }

    @Override
    public Set<? extends FitnessFunction<TestChromosome>> getUncoveredGoals() {
        return delegate.getUncoveredGoals();
    }

    @Override
    public Set<? extends FitnessFunction<TestChromosome>> getCurrentGoals() {
        return delegate.getCurrentGoals();
    }

    @Override
    public Set<? extends FitnessFunction<TestChromosome>> getCoveredGoals() {
        return delegate.getCoveredGoals();
    }
}
