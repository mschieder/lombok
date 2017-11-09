package lombok.core.meta;

import java.util.HashMap;
import java.util.Map;

public class MetaAnnotationRegistry {
    private static Map<String, String> metaAnnotationWithParamsMap = new HashMap<String, String>();

    private MetaAnnotationRegistry() {
        //no instances
    }

    public static void register(Map<String, String> metaAnnoatations) {
        metaAnnotationWithParamsMap.putAll(metaAnnoatations);
    }

    public static String getName(String metaAnnotation) {
        return metaAnnotationWithParamsMap.get(metaAnnotation);
    }
}
