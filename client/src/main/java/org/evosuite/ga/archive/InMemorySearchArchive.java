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
package org.evosuite.ga.archive;

import org.evosuite.ga.Chromosome;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.utils.Randomness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A simple, generic, instance-scoped {@link SearchArchive}: one best-known solution per target,
 * kept in a plain map, with no EvoSuite-specific bookkeeping (no penalty scoring, no
 * {@code TestCluster} interaction, etc.).
 * <p>
 * This is the default archive used by {@code MOSA}/{@code DynaMOSA} when a consumer does not
 * supply its own {@link SearchArchive}, so that the algorithms work out of the box for any
 * {@code Chromosome<T>}/{@code FitnessFunction<T>} pair. EvoSuite's own algorithm instances use
 * {@link EvoSuiteArchiveAdapter} instead, to keep sharing the single process-wide archive that the
 * rest of EvoSuite's test-generation pipeline (coverage-fitness classes, statistics, etc.) also
 * reads from and writes to.
 *
 * @param <T> the chromosome type this archive stores solutions for
 */
public class InMemorySearchArchive<T extends Chromosome<T>> implements SearchArchive<T> {

    private static final long serialVersionUID = 1L;

    private final Map<FitnessFunction<T>, T> covered = new LinkedHashMap<>();
    private final Set<FitnessFunction<T>> uncovered = new LinkedHashSet<>();

    @Override
    public void addTarget(FitnessFunction<T> target) {
        if (!covered.containsKey(target)) {
            uncovered.add(target);
        }
    }

    @Override
    public void addTargets(Collection<? extends FitnessFunction<T>> targets) {
        targets.forEach(this::addTarget);
    }

    @Override
    public void updateArchive(FitnessFunction<T> target, T solution, double fitnessValue) {
        T current = covered.get(target);
        if (current == null || isBetterThanCurrent(current, solution)) {
            covered.put(target, solution);
        }
        uncovered.remove(target);
    }

    @Override
    public boolean isArchiveEmpty() {
        return covered.isEmpty();
    }

    @Override
    public int getNumberOfTargets() {
        return covered.size() + uncovered.size();
    }

    @Override
    public int getNumberOfCoveredTargets() {
        return covered.size();
    }

    @Override
    public Set<FitnessFunction<T>> getCoveredTargets() {
        return new LinkedHashSet<>(covered.keySet());
    }

    @Override
    public int getNumberOfUncoveredTargets() {
        return uncovered.size();
    }

    @Override
    public Set<FitnessFunction<T>> getUncoveredTargets() {
        return new LinkedHashSet<>(uncovered);
    }

    @Override
    public boolean hasTarget(FitnessFunction<T> target) {
        return covered.containsKey(target) || uncovered.contains(target);
    }

    @Override
    public int getNumberOfSolutions() {
        return new LinkedHashSet<>(covered.values()).size();
    }

    @Override
    public Set<T> getSolutions() {
        return new LinkedHashSet<>(covered.values());
    }

    @Override
    public boolean hasSolution(FitnessFunction<T> target) {
        return covered.containsKey(target);
    }

    @Override
    public T getRandomSolution() {
        if (covered.isEmpty()) {
            return null;
        }
        return Randomness.choice(new ArrayList<>(covered.values())).clone();
    }

    @Override
    public boolean isBetterThanCurrent(T currentSolution, T candidateSolution) {
        // No EvoSuite-specific penalty/secondary-objective bookkeeping here: shorter/equal and
        // not worse is considered "better", falling back to "prefer the new one" ties, mirroring
        // the simple archive external consumers already build for themselves.
        if (currentSolution.equals(candidateSolution)) {
            return false;
        }
        return candidateSolution.size() <= currentSolution.size();
    }
}
