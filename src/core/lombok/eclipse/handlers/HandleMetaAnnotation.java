package lombok.eclipse.handlers;

import lombok.AccessLevel;
import lombok.core.AnnotationValues;
import lombok.core.debug.ProblemReporter;
import lombok.eclipse.EclipseAnnotationHandler;
import lombok.eclipse.EclipseNode;
import lombok.experimental.MetaAnnotation;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.mangosdk.spi.ProviderFor;

@ProviderFor(EclipseAnnotationHandler.class)
public class HandleMetaAnnotation extends EclipseAnnotationHandler<MetaAnnotation>{

    private boolean notAClass(EclipseNode typeNode){
        TypeDeclaration typeDecl = null;
        if (typeNode.get() instanceof TypeDeclaration) typeDecl = (TypeDeclaration) typeNode.get();
        int modifiers = typeDecl == null ? 0 : typeDecl.modifiers;
        boolean notAClass = (modifiers &
                (ClassFileConstants.AccInterface | ClassFileConstants.AccAnnotation | ClassFileConstants.AccEnum)) != 0;

        return notAClass;
    }

    @Override
    public void handle(AnnotationValues<MetaAnnotation> annotation, Annotation ast, EclipseNode annotationNode) {
        EclipseNode typeNode = annotationNode.up();

        log("in handle with node: " + typeNode.toString());
        if (notAClass(typeNode)){
             return;
        }
        log("adding getter/setter for node: " + typeNode.toString());
        //
        new HandleGetter().generateGetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true);
        new HandleSetter().generateSetterForType(typeNode, annotationNode, AccessLevel.PUBLIC, true);

    }


    private void log(String message){
        ProblemReporter.info("HandleMetaAnnotation: " + message, null);
    }
}
