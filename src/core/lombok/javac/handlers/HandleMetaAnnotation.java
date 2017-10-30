package lombok.javac.handlers;

import com.sun.tools.javac.tree.JCTree;
import lombok.AccessLevel;
import lombok.core.AnnotationValues;
import lombok.experimental.MetaAnnotation;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import org.mangosdk.spi.ProviderFor;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleMetaAnnotation extends JavacAnnotationHandler<MetaAnnotation> {

    @Override
    public void handle(AnnotationValues<MetaAnnotation> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {
        JavacNode typeNode = annotationNode.up();
        log("in handle with node: " + typeNode.toString());

        if (!JavacHandlerUtil.isClass(typeNode)){
            return;
        }
        log("adding getter/setter for node: " + typeNode.toString());
        //
        new HandleGetter().generateGetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true);
        new HandleSetter().generateSetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true);
    }

    @Override
    public Class<MetaAnnotation> getAnnotationHandledByThisHandler() {
        return super.getAnnotationHandledByThisHandler();
    }

    private void log(String message){
        System.out.println("HandleMetaAnnotation: " + message);
    }
}
