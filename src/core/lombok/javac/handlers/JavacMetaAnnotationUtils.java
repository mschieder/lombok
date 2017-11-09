package lombok.javac.handlers;

import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import lombok.core.meta.SimpleAnnotationParser;
import lombok.core.meta.SimpleAnnotationParserException;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class JavacMetaAnnotationUtils {

    private JavacMetaAnnotationUtils() {
        //no instances
    }

    public static JCTree.JCAnnotation addAnnotation(JavacNode node, String annotationString) throws SimpleAnnotationParserException {
        SimpleAnnotationParser.Anno parsedAnnotation = SimpleAnnotationParser.parseEntry(annotationString, node);
        return addAnnotationFromMetadata(parsedAnnotation, node);
    }

    public static JCTree.JCAnnotation addAnnotationFromMetadata(SimpleAnnotationParser.Anno anno, JavacNode node) {
        Map<String, Object> paramMap = new LinkedHashMap<String, Object>();
        HandleMetaAnnotation.log("addAnnotationFromMetadata: " + anno.getName());
        if (anno.getSingleValue() != null) {
            paramMap.put("value", convertMetadataValue(anno.getSingleValue(), node));
        } else {
            for (Map.Entry<String, SimpleAnnotationParser.Value> next : anno.getParams().entrySet()) {
                paramMap.put(next.getKey(), convertMetadataValue(next.getValue(), node));
            }
        }
        return addAnnotation(node, anno.getName(), paramMap);
    }

    private static JCTree.JCExpression convertMetadataValue(SimpleAnnotationParser.Value valueMetadata, JavacNode node) {
        switch (valueMetadata.getType()) {
            case ARRAY:
                ArrayList<JCTree.JCExpression> list = new ArrayList<JCTree.JCExpression>();
                for (SimpleAnnotationParser.Value next : valueMetadata.getArrayValues()) {
                    list.add(convertMetadataValue(next, node));
                }
                return array(node, list.toArray(new JCTree.JCExpression[list.size()]));
            case FIELD:
                String typeName = valueMetadata.getField().substring(0, valueMetadata.getField().lastIndexOf("."));
                String fieldName = valueMetadata.getField().substring(valueMetadata.getField().lastIndexOf(".") + 1);
                return fieldAccess(typeName, fieldName, node);
            case LITERAL:
                Object literalValue = valueMetadata.getLiteral();
                return node.getTreeMaker().Literal(literalValue);
            default:
                throw new RuntimeException("unknown type " + valueMetadata.getType());
        }
    }


    /**
     * adds an annotation to the given field. if the annotation already exists no change will happen.
     * adaption of method JavacHandlerUtil.addSuppressWarningsAll()
     */
    private static JCTree.JCAnnotation addAnnotation(JavacNode node, String annotationClassName, Map<String, Object> parameters) {
        JCTree.JCModifiers mods;
        int pos;
        if (node.get() instanceof JCTree.JCClassDecl) {
            JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) node.get();
            mods = classDecl.mods;
            pos = classDecl.pos;
        } else {
            JCTree.JCVariableDecl fieldDecl = (JCTree.JCVariableDecl) node.get();
            mods = fieldDecl.mods;
            pos = fieldDecl.pos;
        }
        return addAnnotation(annotationClassName.split("\\."), parameters, mods, node, pos, JavacHandlerUtil.getGeneratedBy(node.get()), node.getContext());
    }

    /**
     * adds an annotation to the given field
     * adaption of method JavacHandlerUtil.addSuppressWarningsAll
     */
    private static JCTree.JCAnnotation addAnnotation(String[] annotationClassName, Map<String, Object> parameters, JCTree.JCModifiers mods, JavacNode node, int pos, JCTree source, Context context) {

        for (JCTree.JCAnnotation ann : mods.annotations) {
            JCTree annType = ann.getAnnotationType();
            Name lastPart = null;
            if (annType instanceof JCTree.JCIdent) lastPart = ((JCTree.JCIdent) annType).name;
            else if (annType instanceof JCTree.JCFieldAccess) lastPart = ((JCTree.JCFieldAccess) annType).name;

            if (lastPart != null && lastPart.contentEquals(annotationClassName[annotationClassName.length - 1]))
                return null;
        }
        JavacTreeMaker maker = node.getTreeMaker();
        JCTree.JCExpression columnType = JavacHandlerUtil.chainDots(node, annotationClassName);
        JavacElements elements = JavacElements.instance(context);

        List<JCTree.JCExpression> args = makeParams(parameters, maker, elements);
        JCTree.JCAnnotation annotation = JavacHandlerUtil.recursiveSetGeneratedBy(maker.Annotation(columnType, args), source, context);

        annotation.pos = pos;
        HandleMetaAnnotation.log("appending annotation " + annotation);
        mods.annotations = mods.annotations.append(annotation);

        return annotation;
    }

    private static JCTree.JCFieldAccess fieldAccess(String typeName, String fieldName, JavacNode node) {
        JavacTreeMaker maker = node.getTreeMaker();
        return maker.Select(JavacHandlerUtil.chainDots(node, typeName.split("\\.")), node.toName(fieldName));
    }

    public static JCTree.JCNewArray array(JavacNode node, JCTree.JCExpression... expr) {
        JavacTreeMaker maker = node.getTreeMaker();
        List<JCTree.JCExpression> list = expr != null ? List.from(expr) : List.<JCTree.JCExpression>nil();
        return maker.NewArray(null, List.<JCTree.JCExpression>nil(), list);
    }

    private static List<JCTree.JCExpression> makeParams(Map<String, Object> parameters, JavacTreeMaker maker, JavacElements elements) {
        List<JCTree.JCExpression> result = List.nil();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            result = result.prepend(assignment(entry.getKey(), entry.getValue(), maker, elements));
        }
        return result;
    }

    private static JCTree.JCAssign assignment(String key, Object value, JavacTreeMaker maker, JavacElements elements) {
        JCTree.JCExpression val;
        if (value instanceof String) {
            val = maker.Literal(value);
        } else {
            val = (JCTree.JCExpression) value;
        }
        return maker.Assign(maker.Ident(elements.getName(key)), val);
    }
}
