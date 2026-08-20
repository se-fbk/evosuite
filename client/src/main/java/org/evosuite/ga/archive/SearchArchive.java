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

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

/**
 * A generic, chromosome-agnostic archive of the best-known solution(s) per target/objective.
 * <p>
 * This is the many-objective-search concept behind EvoSuite's own {@link Archive} (a partial
 * mapping of targets onto the best test cases covering them), extracted into a type-parameterized
 * interface so that {@link org.evosuite.ga.metaheuristics.mosa.AbstractMOSA} and other
 * many-objective algorithms can depend on it without being tied to EvoSuite's test-generation
 * types ({@code TestChromosome}/{@code TestFitnessFunction}).
 * <p>
 * EvoSuite's own algorithms use {@link EvoSuiteArchiveAdapter}, which implements this interface by
 * delegating to the existing static {@link Archive#getArchiveInstance()} singleton, so their
 * behavior is unchanged. A consumer reusing the many-objective algorithms outside of EvoSuite's
 * test-generation pipeline (with its own {@code Chromosome<T>}/{@code FitnessFunction<T>} types)
 * supplies its own implementation instead.
 *
 * @param <T> the chromosome type this archive stores solutions for
 */
public interface SearchArchive<T extends Chromosome<T>> extends Serializable {

    /**
     * Registers a target with the archive.
     *
     * @param target the target to register
     */
    void addTarget(FitnessFunction<T> target);

    /**
     * Registers a collection of targets with the archive.
     *
     * @param targets the targets to register
     */
    void addTargets(Collection<? extends FitnessFunction<T>> targets);

    /**
     * Updates the archive by adding a chromosome solution that covers a target, or by replacing
     * an existing solution if the new one is better.
     *
     * @param target       the covered target
     * @param solution     the solution covering the target
     * @param fitnessValue the fitness value obtained by {@code solution} for {@code target}
     */
    void updateArchive(FitnessFunction<T> target, T solution, double fitnessValue);

    /**
     * @return {@code true} if there is no solution in the archive, {@code false} otherwise
     */
    boolean isArchiveEmpty();

    /**
     * @return the total number of targets (either covered by any solution or not)
     */
    int getNumberOfTargets();

    /**
     * @return the total number of targets covered by all solutions in the archive
     */
    int getNumberOfCoveredTargets();

    /**
     * @return the union of all targets covered by all solutions in the archive
     */
    Set<FitnessFunction<T>> getCoveredTargets();

    /**
     * @return the total number of targets that have not been covered by any solution
     */
    int getNumberOfUncoveredTargets();

    /**
     * @return a set of all targets that have not been covered by any solution
     */
    Set<FitnessFunction<T>> getUncoveredTargets();

    /**
     * @param target the target to check
     * @return {@code true} if the archive contains the specific target, {@code false} otherwise
     */
    boolean hasTarget(FitnessFunction<T> target);

    /**
     * @return the number of unique solutions in the archive
     */
    int getNumberOfSolutions();

    /**
     * @return the union of all solutions in the archive
     */
    Set<T> getSolutions();

    /**
     * @param target the target to check
     * @return {@code true} if the archive has a solution for the specific target, {@code false}
     * otherwise
     */
    boolean hasSolution(FitnessFunction<T> target);

    /**
     * @return the clone of a solution selected at random, or {@code null} if the archive is empty
     */
    T getRandomSolution();

    /**
     * Checks whether a candidate solution is better than an existing one.
     *
     * @param currentSolution   the solution currently in the archive
     * @param candidateSolution the candidate solution
     * @return {@code true} if the candidate solution is better than the existing one
     */
    boolean isBetterThanCurrent(T currentSolution, T candidateSolution);
}
