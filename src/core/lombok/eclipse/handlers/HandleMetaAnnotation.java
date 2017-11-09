package lombok.eclipse.handlers;

import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.core.LombokNode;
import lombok.core.meta.MetaAnnotationRegistry;
import lombok.core.debug.ProblemReporter;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseMetaAnnotationUtils;
import lombok.eclipse.EclipseNode;
import lombok.experimental.MetaAnnotation;
import lombok.eclipse.HandlerLibrary;
import lombok.core.meta.SimpleAnnotationParser;
import lombok.core.meta.SimpleAnnotationParserException;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.mangosdk.spi.ProviderFor;

import java.util.ArrayList;
import java.util.List;

import static lombok.ConfigurationKeys.EXPERIMENTAL_META_ANNOTATION;

@ProviderFor(EclipseAnnotationHandler.class)
public class HandleMetaAnnotation extends EclipseAnnotationHandler<MetaAnnotation> {

    @Override
    public void handle(AnnotationValues<MetaAnnotation> annotation, Annotation ast, EclipseNode annotationNode) {
        EclipseNode node = annotationNode.up();

        if (annotationNode.toString().contains("@" + MetaAnnotation.class.getName()) || annotationNode.toString().contains("@" + MetaAnnotation.class.getSimpleName())) {
            return;
        }
        log("in handle with annotationNode: " + annotationNode.get().toString());

        String fullQualifiedAnnotationName = getFullQualifiedName(annotationNode, node);
        log("fully qualified name " + fullQualifiedAnnotationName);

        String name = MetaAnnotationRegistry.getName(fullQualifiedAnnotationName);
        if (name == null) {
            node.addError("cannot find meta-annotation profile name for annotation " + fullQualifiedAnnotationName);
            return;
        }

        List<String> annotations = readAllAnnotationsFor(name, node);
        log("read meta-annotation profile " + name + ": " + annotations);

        List<EclipseNode> lombokNodes = new ArrayList<EclipseNode>();
        for (String next : annotations) {
            try {
                SimpleAnnotationParser.Anno anno = SimpleAnnotationParser.parseEntry(next, node);
                Annotation eclipseAnnotation = EclipseMetaAnnotationUtils.addAnnotationFromMetadata(anno, node);

                EclipseNode addedNode = node.add(eclipseAnnotation, AST.Kind.ANNOTATION);
                if (anno.getName().startsWith("lombok.")) {
                    lombokNodes.add(addedNode);
                }
            } catch (SimpleAnnotationParserException e) {
                node.addError("error while adding annotation " + next + ": " + e.getMessage());
                return;
            }
        }

        //handle lombok annotations
        HandlerLibrary handlerLibrary = HandlerLibrary.getLastLoaded();
        for (long priority : handlerLibrary.getPriorities()) {
            for (EclipseNode nextNode : lombokNodes)
                handlerLibrary.handleAnnotation(null, nextNode, (Annotation) nextNode.get(), priority);
        }
    }

    private String getFullQualifiedName(LombokNode<?, ?, ?> annotationNode, LombokNode<?, ?, ?> node) {
        String annotationName = annotationNode.get().toString().replace("@", "");
        if (annotationName.contains("(")){
            annotationName = annotationName.substring(0,annotationName.indexOf('('));
        }
        String fullQualifiedAnnotationName = node.getImportList().getFullyQualifiedNameForSimpleName(annotationName);
        return fullQualifiedAnnotationName != null ? fullQualifiedAnnotationName : node.getPackageDeclaration() + "." + annotationName;
    }


    private List<String> readAllAnnotationsFor(String name, EclipseNode typeNode) {
        List<String> result = new ArrayList<String>();
        for (String next : typeNode.getAst().readConfiguration(EXPERIMENTAL_META_ANNOTATION)) {
            if (next.startsWith(name + ":")) {
                result.add(next.substring(name.length() + 1).trim());
            }
        }
        return result;
    }


    private static void log(String message) {
        ProblemReporter.info("HandleMetaAnnotation: " + message, null);
    }
}
