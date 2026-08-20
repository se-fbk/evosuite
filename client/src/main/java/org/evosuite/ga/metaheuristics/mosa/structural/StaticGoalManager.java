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
import org.evosuite.ga.archive.SearchArchive;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A trivial {@link GoalManager}: every target is a "current goal" from the start, with no
 * dependency-driven narrowing of the search frontier. This is equivalent to plain (non-dynamic)
 * MOSA - it is the sensible default for {@code DynaMOSA} when a consumer has no dependency
 * relation between goals to exploit (e.g. no notion of "control dependency" for their domain).
 *
 * @param <T> the chromosome type being evolved
 */
public class StaticGoalManager<T extends Chromosome<T>> implements GoalManager<T> {

    private static final long serialVersionUID = 1L;

    private final SearchArchive<T> archive;

    public StaticGoalManager(SearchArchive<T> archive, Collection<? extends FitnessFunction<T>> targets) {
        this.archive = archive;
        this.archive.addTargets(targets);
    }

    @Override
    public void calculateFitness(T c, GeneticAlgorithm<T> ga) {
        for (FitnessFunction<T> goal : getCurrentGoals()) {
            double fitness = goal.getFitness(c);
            boolean covered = goal.isMaximizationFunction() ? fitness >= 1.0 : fitness == 0.0;
            if (covered) {
                archive.updateArchive(goal, c, fitness);
            }
        }
    }

    @Override
    public Set<? extends FitnessFunction<T>> getUncoveredGoals() {
        return new LinkedHashSet<>(archive.getUncoveredTargets());
    }

    @Override
    public Set<? extends FitnessFunction<T>> getCurrentGoals() {
        Set<FitnessFunction<T>> all = new LinkedHashSet<>(archive.getCoveredTargets());
        all.addAll(archive.getUncoveredTargets());
        return all;
    }

    @Override
    public Set<? extends FitnessFunction<T>> getCoveredGoals() {
        return new LinkedHashSet<>(archive.getCoveredTargets());
    }
}
