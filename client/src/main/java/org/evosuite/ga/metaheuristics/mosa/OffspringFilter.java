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

import org.evosuite.ga.Chromosome;

import java.io.Serializable;

/**
 * A hook, applied by {@link AbstractMOSA} to every freshly bred offspring (after crossover, before
 * fitness evaluation), for domain-specific mutation and refinement.
 * <p>
 * EvoSuite's own {@code TestChromosome}-based MOSA/DynaMOSA use this to: strip now-unused
 * statements left behind by crossover, mutate the offspring (retrying once if the first attempt
 * had no effect), repair/reject offspring that no longer contain any call to the class under test
 * (mutation/crossover can otherwise degenerate a test case into pure primitive-statement noise
 * that could never cover anything), and clear cached mutation/execution results. A consumer with a
 * different chromosome representation either supplies an equivalent domain-specific
 * implementation, or uses {@link #mutateOnly()} (a reasonable, chromosome-agnostic default).
 *
 * @param <T> the chromosome type being evolved
 */
public interface OffspringFilter<T extends Chromosome<T>> extends Serializable {

    /**
     * @param offspring the offspring to prepare (already cloned from, and possibly crossed over
     *                  with, {@code parent}); implementations are expected to mutate it in place
     * @param parent    the parent chromosome {@code offspring} was derived from
     * @return the chromosome to keep evaluating (usually {@code offspring} itself), or
     * {@code null} to discard it entirely
     */
    T prepareOffspring(T offspring, T parent);

    /**
     * @param <T> the chromosome type
     * @return a filter that just mutates the offspring (retrying once if the first mutation had
     * no effect) and performs no other refinement
     */
    static <T extends Chromosome<T>> OffspringFilter<T> mutateOnly() {
        return (offspring, parent) -> {
            offspring.mutate();
            if (!offspring.isChanged()) {
                offspring.mutate();
            }
            return offspring;
        };
    }
}
