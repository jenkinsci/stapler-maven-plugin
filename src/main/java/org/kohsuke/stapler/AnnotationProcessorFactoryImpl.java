package org.kohsuke.stapler;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.mirror.apt.AnnotationProcessors;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Kohsuke Kawaguchi
 */
final class AnnotationProcessorFactoryImpl implements AnnotationProcessorFactory {
    private List<AnnotationProcessorFactory> factories;

    public AnnotationProcessorFactoryImpl(List<AnnotationProcessorFactory> factories) {
        this.factories = factories;
    }

    public Collection<String> supportedOptions() {
        return Collections.emptyList();
    }

    public Collection<String> supportedAnnotationTypes() {
        return Collections.singleton("*");
    }

    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> set, AnnotationProcessorEnvironment env) {
        List<AnnotationProcessor> processors = new ArrayList<AnnotationProcessor>();
        processors.add(new ExportedBeanAnnotationProcessor(env));
        processors.add(new ConstructorProcessor(env));

        for (AnnotationProcessorFactory f : factories)
            processors.add(f.getProcessorFor(set,env));

        return AnnotationProcessors.getCompositeAnnotationProcessor(processors);
    }
}
