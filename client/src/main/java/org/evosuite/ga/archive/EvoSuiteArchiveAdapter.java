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

import org.evosuite.ga.FitnessFunction;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.secondaryobjectives.TestCaseSecondaryObjective;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridges the generic {@link SearchArchive} interface onto EvoSuite's own static
 * {@link Archive#getArchiveInstance()} singleton, so that EvoSuite's own many-objective algorithms
 * (MOSA, DynaMOSA) keep their exact existing archiving behavior - all bookkeeping (penalty scoring,
 * secondary objectives, method-call tracking in {@link org.evosuite.setup.TestCluster}, the
 * {@code Properties.ARCHIVE_TYPE}-selected {@code CoverageArchive}/{@code MIOArchive} backing
 * store) is untouched; this class only translates between {@code FitnessFunction<TestChromosome>}
 * and the concrete {@code TestFitnessFunction} type the legacy {@link Archive} API expects.
 * <p>
 * This is a singleton-per-JVM adapter (mirroring the singleton it wraps), used as the default
 * {@link SearchArchive} for EvoSuite's own {@code TestChromosome}-based algorithm instances. A
 * consumer reusing the many-objective algorithms with its own chromosome type supplies its own
 * {@link SearchArchive} implementation instead - see {@code AbstractMOSA}.
 */
public final class EvoSuiteArchiveAdapter implements SearchArchive<TestChromosome> {

    private static final long serialVersionUID = 1L;

    private static final EvoSuiteArchiveAdapter instance = new EvoSuiteArchiveAdapter();

    private EvoSuiteArchiveAdapter() {
        // Set the secondary objectives of test cases (useful when MOSA/DynaMOSA compare two test
        // cases to, e.g., update the archive) - see Archive#isBetterThanCurrent.
        TestCaseSecondaryObjective.setSecondaryObjectives();
    }

    public static EvoSuiteArchiveAdapter getInstance() {
        return instance;
    }

    private static TestFitnessFunction asTestFitnessFunction(FitnessFunction<TestChromosome> target) {
        if (!(target instanceof TestFitnessFunction)) {
            throw new IllegalArgumentException(
                    "Only TestFitnessFunctions are supported by EvoSuite's Archive, but got: "
                            + target.getClass().getCanonicalName());
        }
        return (TestFitnessFunction) target;
    }

    @Override
    public void addTarget(FitnessFunction<TestChromosome> target) {
        Archive.getArchiveInstance().addTarget(asTestFitnessFunction(target));
    }

    @Override
    public void addTargets(Collection<? extends FitnessFunction<TestChromosome>> targets) {
        Archive.getArchiveInstance().addTargets(
                targets.stream().map(EvoSuiteArchiveAdapter::asTestFitnessFunction).collect(Collectors.toList()));
    }

    @Override
    public void updateArchive(FitnessFunction<TestChromosome> target, TestChromosome solution,
                               double fitnessValue) {
        Archive.getArchiveInstance().updateArchive(asTestFitnessFunction(target), solution, fitnessValue);
    }

    @Override
    public boolean isArchiveEmpty() {
        return Archive.getArchiveInstance().isArchiveEmpty();
    }

    @Override
    public int getNumberOfTargets() {
        return Archive.getArchiveInstance().getNumberOfTargets();
    }

    @Override
    public int getNumberOfCoveredTargets() {
        return Archive.getArchiveInstance().getNumberOfCoveredTargets();
    }

    @Override
    public Set<FitnessFunction<TestChromosome>> getCoveredTargets() {
        return new LinkedHashSet<>(Archive.getArchiveInstance().getCoveredTargets());
    }

    @Override
    public int getNumberOfUncoveredTargets() {
        return Archive.getArchiveInstance().getNumberOfUncoveredTargets();
    }

    @Override
    public Set<FitnessFunction<TestChromosome>> getUncoveredTargets() {
        return new LinkedHashSet<>(Archive.getArchiveInstance().getUncoveredTargets());
    }

    @Override
    public boolean hasTarget(FitnessFunction<TestChromosome> target) {
        return Archive.getArchiveInstance().hasTarget(asTestFitnessFunction(target));
    }

    @Override
    public int getNumberOfSolutions() {
        return Archive.getArchiveInstance().getNumberOfSolutions();
    }

    @Override
    public Set<TestChromosome> getSolutions() {
        return Archive.getArchiveInstance().getSolutions();
    }

    @Override
    public boolean hasSolution(FitnessFunction<TestChromosome> target) {
        return Archive.getArchiveInstance().hasSolution(asTestFitnessFunction(target));
    }

    @Override
    public TestChromosome getRandomSolution() {
        return Archive.getArchiveInstance().getRandomSolution();
    }

    @Override
    public boolean isBetterThanCurrent(TestChromosome currentSolution, TestChromosome candidateSolution) {
        return Archive.getArchiveInstance().isBetterThanCurrent(currentSolution, candidateSolution);
    }
}
