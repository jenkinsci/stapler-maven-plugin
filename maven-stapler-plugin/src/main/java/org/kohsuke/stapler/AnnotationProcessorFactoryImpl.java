package org.kohsuke.stapler;

import com.sun.mirror.apt.AnnotationProcessorFactory;
import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.AnnotationProcessors;
import com.sun.mirror.declaration.AnnotationTypeDeclaration;

import java.util.Collection;
import java.util.Set;
import java.util.Collections;
import java.util.Arrays;

import org.kohsuke.stapler.export.ExposedBean;
import org.kohsuke.stapler.export.Exposed;

/**
 * @author Kohsuke Kawaguchi
 */
final class AnnotationProcessorFactoryImpl implements AnnotationProcessorFactory {

    public Collection<String> supportedOptions() {
        return Collections.emptyList();
    }

    public Collection<String> supportedAnnotationTypes() {
        return Arrays.asList(ExposedBean.class.getName(), Exposed.class.getName(), DataBoundConstructor.class.getName());
    }

    public AnnotationProcessor getProcessorFor(Set<AnnotationTypeDeclaration> set, AnnotationProcessorEnvironment env) {
        return AnnotationProcessors.getCompositeAnnotationProcessor(
            new ExposedBeanAnnotationProcessor(env),
            new ConstructorProcessor(env));
    }
}
