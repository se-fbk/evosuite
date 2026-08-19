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
package org.evosuite.instrumentation;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.classpath.ResourceList;
import org.evosuite.runtime.instrumentation.RuntimeInstrumentation;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;


/**
 * <em>Note:</em> Do not inadvertently use multiple instances of this class in
 * the application! This may lead to hard to detect and debug errors. Yet this
 * class cannot be an singleton as it might be necessary to do so...
 *
 * @author roessler
 * @author Gordon Fraser
 */
public class InstrumentingClassLoader extends ClassLoader {

    private final static Logger logger = LoggerFactory.getLogger(InstrumentingClassLoader.class);

    private final BytecodeInstrumentation instrumentation;
    private final ClassLoader classLoader;
    private final Map<String, Class<?>> classes = new HashMap<>();

    /**
     * <p>
     * Constructor for InstrumentingClassLoader.
     * </p>
     */
    public InstrumentingClassLoader() {
        this(new BytecodeInstrumentation());
        setClassAssertionStatus(Properties.TARGET_CLASS, true);
        logger.debug("STANDARD classloader running now");
    }

    /**
     * <p>
     * Constructor for InstrumentingClassLoader.
     * </p>
     *
     * @param instrumentation a {@link org.evosuite.instrumentation.BytecodeInstrumentation}
     *                        object.
     */
    public InstrumentingClassLoader(BytecodeInstrumentation instrumentation) {
        super(InstrumentingClassLoader.class.getClassLoader());
        classLoader = InstrumentingClassLoader.class.getClassLoader();
        this.instrumentation = instrumentation;
    }

    public List<String> getViewOfInstrumentedClasses() {
        List<String> list = new ArrayList<>(classes.keySet());
        return list;
    }


    public Class<?> loadClassFromFile(String fullyQualifiedTargetClass, String fileName) throws ClassNotFoundException {

        String className = fullyQualifiedTargetClass.replace('.', '/');

        try (InputStream is = new FileInputStream(new File(fileName))) {

            byte[] byteBuffer = getTransformedBytes(className, is);

            createPackageDefinition(fullyQualifiedTargetClass);
            Class<?> result = defineClass(fullyQualifiedTargetClass, byteBuffer, 0, byteBuffer.length);

            classes.put(fullyQualifiedTargetClass, result);

            logger.info("Loaded class " + fullyQualifiedTargetClass + " directly from " + fileName);
            return result;
        } catch (Throwable t) {
            logger.info("Error while loading class " + fullyQualifiedTargetClass + " : " + t);
            throw new ClassNotFoundException(t.getMessage(), t);
        }
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            if ("<evosuite>".equals(name)) {
                throw new ClassNotFoundException();
            }

            if (!RuntimeInstrumentation.checkIfCanInstrument(name)) {
                Class<?> result = findLoadedClass(name);
                if (result != null) {
                    return result;
                }
                result = classLoader.loadClass(name);
                return result;
            }

            Class<?> result = classes.get(name);
            if (result != null) {
                return result;
            } else {
                logger.info("Seeing class for first time: " + name);
                Class<?> instrumentedClass = instrumentClass(name);
                return instrumentedClass;
            }

        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This loader has no URLs/classpath of its own (see the class Javadoc): {@code loadClass()}
     * resolves target-project classes via a custom mechanism ({@link ResourceList}, backed by
     * {@code Properties.CP}), not via the standard {@code findResource()}/{@code getResource()}
     * APIs. Without this override, {@code getResourceAsStream()} on this loader can only ever see
     * what its parent classloader has - which does not necessarily include the target project's
     * classpath (e.g. when running with {@code client_on_thread=true}, or for the odd resource
     * lookup triggered from a background/RMI thread). This matters because some code (e.g.
     * {@code ComputeClassWriter}, used by ASM to compute common super classes during
     * instrumentation) legitimately needs to read the bytecode of arbitrary classpath classes via
     * {@code getResourceAsStream()}, not via {@code loadClass()}.
     */
    @Override
    public InputStream getResourceAsStream(String name) {
        InputStream is = super.getResourceAsStream(name);
        if (is != null) {
            return is;
        }
        if (name.endsWith(".class")) {
            String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
            return ResourceList.getInstance(TestGenerationContext.getInstance().getClassLoaderForSUT())
                    .getClassAsStream(className);
        }
        return null;
    }

    //This is needed, as it is overridden in subclasses
    protected byte[] getTransformedBytes(String className, InputStream is) throws IOException {
        return instrumentation.transformBytes(this, className, new ClassReader(is));
    }

    private Class<?> instrumentClass(String fullyQualifiedTargetClass) throws ClassNotFoundException {
        String className = fullyQualifiedTargetClass.replace('.', '/');
        InputStream is = null;
        try {
            is = ResourceList.getInstance(TestGenerationContext.getInstance().getClassLoaderForSUT()).getClassAsStream(fullyQualifiedTargetClass);

            if (is == null) {
                throw new ClassNotFoundException("Class '" + className + ".class"
                        + "' should be in target project, but could not be found!");
            }

            byte[] byteBuffer = getTransformedBytes(className, is);
            createPackageDefinition(fullyQualifiedTargetClass);
            Class<?> result = defineClass(fullyQualifiedTargetClass, byteBuffer, 0, byteBuffer.length);
            classes.put(fullyQualifiedTargetClass, result);

            logger.info("Loaded class: " + fullyQualifiedTargetClass);
            return result;
        } catch (Throwable t) {
            logger.info("Error while loading class: " + t);
            throw new ClassNotFoundException(t.getMessage(), t);
        } finally {
            if (is != null)
                try {
                    is.close();
                } catch (IOException e) {
                    throw new Error(e);
                }
        }
    }

    /**
     * Before a new class is defined, we need to create a package definition for it
     *
     * @param className
     */
    private void createPackageDefinition(String className) {
        int i = className.lastIndexOf('.');
        if (i != -1) {
            String pkgname = className.substring(0, i);
            // Check if package already loaded.
            Package pkg = getPackage(pkgname);
            if (pkg == null) {
                definePackage(pkgname, null, null, null, null, null, null, null);
                logger.info("Defined package (3): " + getPackage(pkgname) + ", " + getPackage(pkgname).hashCode());
            }
        }
    }

    public BytecodeInstrumentation getInstrumentation() {
        return instrumentation;
    }

    public Set<String> getLoadedClasses() {
        HashSet<String> loadedClasses = new HashSet<>(this.classes.keySet());
        return loadedClasses;
    }

}
