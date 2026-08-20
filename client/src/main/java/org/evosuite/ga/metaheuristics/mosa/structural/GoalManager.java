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

import org.evosuite.ga.Chromosome;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;

import java.io.Serializable;
import java.util.Set;

/**
 * Manages which targets/goals a many-objective search is currently optimizing for.
 * <p>
 * This captures DynaMOSA's defining idea in chromosome-agnostic form: rather than treating every
 * target as an objective from the start, a {@code GoalManager} maintains a (possibly narrower)
 * frontier of "current goals" - typically those free of dependencies on other, not-yet-covered
 * goals - and expands that frontier as goals get covered.
 * <p>
 * EvoSuite's own {@link MultiCriteriaManager} is one implementation, deriving the goal-dependency
 * relation from branch/control-dependency information specific to Java bytecode coverage. A
 * consumer reusing DynaMOSA outside of EvoSuite's test-generation pipeline supplies its own
 * implementation (see {@link StaticGoalManager} for a trivial "all goals are current from the
 * start" default, equivalent to plain, non-dynamic MOSA).
 *
 * @param <T> the chromosome type being evolved
 */
public interface GoalManager<T extends Chromosome<T>> extends Serializable {

    /**
     * Evaluates {@code c} against the current goals, updates the archive of covered goals, and
     * expands the frontier of current goals as appropriate.
     *
     * @param c  the chromosome to evaluate
     * @param ga the search algorithm driving the evaluation
     */
    void calculateFitness(T c, GeneticAlgorithm<T> ga);

    /**
     * @return the goals that have not yet been covered by any solution
     */
    Set<? extends FitnessFunction<T>> getUncoveredGoals();

    /**
     * @return the subset of uncovered goals currently being targeted (the search frontier)
     */
    Set<? extends FitnessFunction<T>> getCurrentGoals();

    /**
     * @return the goals that have already been covered by some solution
     */
    Set<? extends FitnessFunction<T>> getCoveredGoals();
}
