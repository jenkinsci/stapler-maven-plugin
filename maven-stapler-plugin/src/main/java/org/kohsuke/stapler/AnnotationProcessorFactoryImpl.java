package org.kohsuke.stapler;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.mirror.apt.AnnotationProcessors;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 */
public final class AnnotationProcessorFactoryImpl implements AnnotationProcessorFactory {
    public Collection<String> supportedOptions() {
        return Collections.emptyList();
    }

    public Collection<String> supportedAnnotationTypes() {
        return Collections.singleton("*");
    }

    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> set, AnnotationProcessorEnvironment env) {
        return AnnotationProcessors.getCompositeAnnotationProcessor(
            new ExportedBeanAnnotationProcessor(env),
            new ConstructorProcessor(env),
            new QueryParameterAnnotationProcessor(env)
        );
    }
}
