package lombok.core.meta;

import lombok.experimental.MetaAnnotation;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Javac Annotation Processor for @{@link lombok.experimental.MetaAnnotation}s.
 * Must run before the "normal" lombok Processors
 * the result set is stored in the system property "lombok.meta-annotations" as string and can be retrieved with the getAllMetaAnnotationsFromSystemProperty
 *
 * @author Michael Schieder
 */
@SupportedAnnotationTypes("lombok.experimental.MetaAnnotation")
public class MetaAnnotationProcessor extends AbstractProcessor {
    private static final String META_ANNOTATIONS_SYSTEMPROPERTY = "lombok.meta-annotations";

    public static Map<String, String> getAllMetaAnnotationsFromSystemProperty() {
        Map<String, String> result = new LinkedHashMap<String, String>();
        String string = System.getProperty(META_ANNOTATIONS_SYSTEMPROPERTY);
        if (string != null) {
            for (String next : Arrays.asList(string.replaceAll("[\\{|\\}]", "").split("\\s*,\\s*"))) {
                String[] kv = next.split("=");
                result.put(kv[0].trim(), kv[1].trim());
            }
        }
        return result;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!annotations.iterator().hasNext()) {
            return false;
        }
        TypeElement annotation = annotations.iterator().next();

        Map<String, String> set = new LinkedHashMap<String, String>();
        for (Element next : roundEnv.getElementsAnnotatedWith(annotation)) {
            String name = next.getAnnotation(MetaAnnotation.class).name();
            set.put(next.toString(), name);
        }
        System.setProperty(META_ANNOTATIONS_SYSTEMPROPERTY, set.toString());

        return true;
    }

}
