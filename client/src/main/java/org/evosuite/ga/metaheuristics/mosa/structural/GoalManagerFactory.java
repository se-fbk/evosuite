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

import java.io.Serializable;
import java.util.List;

/**
 * Creates a {@link GoalManager} for a given list of targets. {@code DynaMOSA} needs the targets
 * (its {@code fitnessFunctions}) to have been set before the manager can be built, so construction
 * is deferred via this factory rather than happening in the algorithm's own constructor.
 *
 * @param <T> the chromosome type being evolved
 */
@FunctionalInterface
public interface GoalManagerFactory<T extends Chromosome<T>> extends Serializable {

    GoalManager<T> create(List<? extends FitnessFunction<T>> targets);
}
