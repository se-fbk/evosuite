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
import org.evosuite.testcase.TestCase;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.statements.ArrayStatement;
import org.evosuite.testcase.statements.ConstructorStatement;
import org.evosuite.testcase.statements.MethodStatement;
import org.evosuite.testcase.statements.PrimitiveStatement;
import org.evosuite.testcase.statements.Statement;
import org.evosuite.testcase.variable.VariableReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EvoSuite's {@code TestChromosome}-specific offspring mutation and refinement, used by
 * {@code MOSA}/{@code DynaMOSA} within EvoSuite's own test-generation pipeline. This is a faithful
 * extraction of the breeding refinements that used to be hard-coded inline in
 * {@code AbstractMOSA} - see {@link OffspringFilter} for the generic hook this implements.
 */
public class TestChromosomeOffspringFilter implements OffspringFilter<TestChromosome> {

    private static final long serialVersionUID = 1L;

    @Override
    public TestChromosome prepareOffspring(TestChromosome offspring, TestChromosome parent) {
        removeUnusedVariables(offspring);
        mutate(offspring, parent);
        clearCachedResults(offspring);
        return offspring;
    }

    /**
     * Method used to mutate an offspring.
     *
     * @param offspring the offspring chromosome
     * @param parent    the parent chromosome {@code offspring} was created from
     */
    private void mutate(TestChromosome offspring, TestChromosome parent) {
        offspring.mutate();
        if (!offspring.isChanged()) {
            // if offspring is not changed, we try to mutate it once again
            offspring.mutate();
        }
        if (!hasMethodCall(offspring)) {
            offspring.setTestCase(parent.getTestCase().clone());
            boolean changed = offspring.mutationInsert();
            if (changed) {
                offspring.getTestCase().forEach(Statement::isValid);
            }
            offspring.setChanged(changed);
        }
    }

    /**
     * This method checks whether the test has only primitive type statements. Indeed,
     * crossover and mutation can lead to tests with no method calls (methods or constructors
     * call), thus, when executed they will never cover something in the class under test.
     *
     * @param test to check
     * @return true if the test has at least one method or constructor call (i.e., the test may
     * cover something when executed; false otherwise
     */
    private boolean hasMethodCall(TestChromosome test) {
        TestCase tc = test.getTestCase();
        for (Statement s : tc) {
            if (s instanceof MethodStatement) {
                MethodStatement ms = (MethodStatement) s;
                if (ms.getDeclaringClassName().equals(Properties.TARGET_CLASS)) {
                    return true;
                }
            }
            if (s instanceof ConstructorStatement) {
                ConstructorStatement ms = (ConstructorStatement) s;
                if (ms.getDeclaringClassName().equals(Properties.TARGET_CLASS)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This method clears the cached results for a specific chromosome (e.g., fitness function
     * values computed in previous generations). Since a test case is changed via crossover
     * and/or mutation, previous data must be recomputed.
     *
     * @param chromosome TestChromosome to clean
     */
    private void clearCachedResults(TestChromosome chromosome) {
        chromosome.clearCachedMutationResults();
        chromosome.clearCachedResults();
        chromosome.clearMutationHistory();
        chromosome.getFitnessValues().clear();
    }

    /**
     * When a test case is changed via crossover and/or mutation, it can contains some
     * primitive variables that are not used as input (or to store the output) of method calls.
     * Thus, this method removes all these "trash" statements.
     *
     * @param chromosome
     */
    private void removeUnusedVariables(TestChromosome chromosome) {
        final TestCase t = chromosome.getTestCase();
        final List<Integer> toDelete = new ArrayList<>(chromosome.size());

        int num = 0;
        for (Statement s : t) {
            final VariableReference var = s.getReturnValue();
            final boolean delete = s instanceof PrimitiveStatement || s instanceof ArrayStatement;
            if (!t.hasReferences(var) && delete) {
                toDelete.add(num);
            }
            num++;
        }
        toDelete.sort(Collections.reverseOrder());
        for (int position : toDelete) {
            t.remove(position);
        }
    }
}
