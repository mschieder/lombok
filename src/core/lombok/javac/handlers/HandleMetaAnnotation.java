package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree;
import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.core.LombokNode;
import lombok.core.meta.MetaAnnotationRegistry;
import lombok.core.meta.SimpleAnnotationParser;
import lombok.core.meta.SimpleAnnotationParserException;
import lombok.experimental.MetaAnnotation;
import lombok.javac.HandlerLibrary;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

import java.util.ArrayList;
import java.util.List;

import static lombok.ConfigurationKeys.EXPERIMENTAL_META_ANNOTATION;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleMetaAnnotation extends JavacAnnotationHandler<MetaAnnotation> {

    @Override
    public void handle(AnnotationValues<MetaAnnotation> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {
        JavacNode node = annotationNode.up();

        if (annotationNode.toString().contains("@" + MetaAnnotation.class.getName()) || annotationNode.toString().contains("@" + MetaAnnotation.class.getSimpleName())){
            return;
        }

        log("in handle with annotationNode: " + annotationNode.get().toString());

        String fullQualifiedAnnotationName = getFullQualifiedName(annotationNode, node);
        log("fully qualified name " + fullQualifiedAnnotationName);

        String name = MetaAnnotationRegistry.getName(fullQualifiedAnnotationName);
        if (name == null){
            node.addError("cannot find meta-annotation profile name for annotation " + fullQualifiedAnnotationName);
            return;
        }

        List<String> annotations = readAllAnnotationsFor(name, node);
        log("read meta-annotation profile " + name + ": " + annotations);


        List<JavacNode> lombokNodes = new ArrayList<JavacNode>();
        for (String next : annotations) {
            try {
                SimpleAnnotationParser.Anno anno = SimpleAnnotationParser.parseEntry(next, node);
                JCTree.JCAnnotation jcAnnotation = JavacMetaAnnotationUtils.addAnnotationFromMetadata(anno, node);
                JavacNode addedNode = node.add(jcAnnotation, AST.Kind.ANNOTATION);

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
        for (long priority: handlerLibrary.getPriorities()){
            for(JavacNode nextNode: lombokNodes)
                handlerLibrary.handleAnnotation(null, nextNode, (JCTree.JCAnnotation) nextNode.get(), priority);
        }
    }

    private List<String> readAllAnnotationsFor(String name, JavacNode typeNode){
        List<String> result = new ArrayList<String>();
        for (String next: typeNode.getAst().readConfiguration(EXPERIMENTAL_META_ANNOTATION)){
            if (next.startsWith(name + ":")){
                result.add(next.substring(name.length() +1).trim());
            }
        }
        return result;
    }

    private String getFullQualifiedName(LombokNode<?, ?, ?> annotationNode, LombokNode<?, ?, ?> node) {
        String annotationName = annotationNode.get().toString().replace("@", "");
        if (annotationName.contains("(")){
            annotationName = annotationName.substring(0,annotationName.indexOf('('));
        }
        String fullQualifiedAnnotationName = node.getImportList().getFullyQualifiedNameForSimpleName(annotationName);
        return fullQualifiedAnnotationName != null ? fullQualifiedAnnotationName : node.getPackageDeclaration() + "." + annotationName;
    }

    public static void log(String message){
        System.out.println("HandleMetaAnnotation: " + message);
    }

}
