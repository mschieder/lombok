package lombok.core;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Javac Annotation Processor for @{@link lombok.experimental.MetaAnnotation}s.
 * Must run before the "normal" lombok Processors
 * the result set is stored in the system property "lombok.meta-annotations" as string and can be retrieved with the getAllMetaAnnotationsFromSystemProperty
 * @author Michael Schieder
 */
@SupportedAnnotationTypes("lombok.experimental.MetaAnnotation")
public class MetaAnnotationProcessor extends AbstractProcessor {
    private static final String META_ANNOTATIONS_SYSTEMPROPERTY = "lombok.meta-annotations";

    public static Set<String> getAllMetaAnnotationsFromSystemProperty() {
        Set<String> result = new HashSet<String>();
        String string = System.getProperty(META_ANNOTATIONS_SYSTEMPROPERTY);
        if (string != null) {
            result.addAll(Arrays.asList(string.replaceAll("[\\[|\\]]", "").split("\\s*,\\s*")));
        }
        return result;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!annotations.iterator().hasNext()) {
            return false;
        }
        TypeElement annotation = annotations.iterator().next();

         Set<String> set = new HashSet<String>();
        for (Element next : roundEnv.getElementsAnnotatedWith(annotation)) {
            set.add(next.toString());
        }
        System.setProperty(META_ANNOTATIONS_SYSTEMPROPERTY, set.toString());

        return true;
    }

}
