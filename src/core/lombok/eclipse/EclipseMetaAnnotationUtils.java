package lombok.eclipse;

import lombok.core.meta.MetaAnnotationProcessor;
import lombok.core.debug.ProblemReporter;
import lombok.experimental.MetaAnnotation;
import lombok.javac.handlers.HandleMetaAnnotation;
import lombok.core.meta.SimpleAnnotationParser;
import lombok.core.meta.SimpleAnnotationParserException;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.ArrayInitializer;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.eclipse.jdt.internal.compiler.ast.MarkerAnnotation;
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.eclipse.jdt.internal.compiler.ast.NormalAnnotation;
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.TrueLiteral;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static lombok.eclipse.handlers.EclipseHandlerUtil.*;

public class EclipseMetaAnnotationUtils {

    static class ParseResult {
        private String packageName;
        private String nameParam;
        private String lineMatch;

        public ParseResult(String packageName, String nameParam, String lineMatch) {
            this.packageName = packageName;
            this.nameParam = nameParam;
            this.lineMatch = lineMatch;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getNameParam() {
            return nameParam;
        }

        public String getLineMatch() {
            return lineMatch;
        }
    }

    private EclipseMetaAnnotationUtils() {
        //no instances needed
    }

    public static Map<String, String> getAllMetaAnnotations() {
        Map<String, String> result = MetaAnnotationProcessor.getAllMetaAnnotationsFromSystemProperty();

        if (result.isEmpty()) {
            try {
                for (IProject nextProject : findAllOpenJavaProjects()) {
                    for (IFile nextFile : collectAllJavaFiles(nextProject)) {
                        ParseResult parseResult = null;
                        if ((parseResult = containsLine("@" + MetaAnnotation.class.getName(), nextFile)) != null ||
                                (parseResult = containsLine("@" + MetaAnnotation.class.getSimpleName(), nextFile)) != null) {
                            StringBuilder typeName = new StringBuilder(parseResult.getPackageName());
                            if (typeName.length() > 0) {
                                typeName.append(".");
                            }
                            typeName.append(nextFile.getName().replace("." + nextFile.getFileExtension(), ""));
                            log("parsed paramname: " + parseResult.getNameParam(), null);
                            result.put(typeName.toString(), parseResult.getNameParam());
                        }
                    }
                }
            } catch (CoreException e) {
                log(e.getMessage(), e);
            } catch (IOException e) {
                log(e.getMessage(), e);
            }
        }
        return result;

    }

    private static List<IProject> findAllOpenJavaProjects() throws CoreException {
        List<IProject> result = new ArrayList<IProject>();
        IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
        for (IProject project : workspaceRoot.getProjects()) {
            if (project.isOpen() && project.hasNature(JavaCore.NATURE_ID)) {
                result.add(project);
            }
        }
        return result;
    }

    private static Set<IFile> collectAllJavaFiles(IContainer container) throws CoreException {
        Set<IFile> result = new LinkedHashSet<IFile>();
        collectAllJavaFiles(container, result);
        return result;
    }

    private static void collectAllJavaFiles(IContainer container, Set<IFile> result) throws CoreException {
        for (IResource member : container.members()) {
            if (member instanceof IContainer) {
                collectAllJavaFiles((IContainer) member, result);
            } else if (member instanceof IFile && "java".equals(member.getFileExtension())) {
                result.add((IFile) member);
            }
        }
    }

    private static void log(String message, Throwable e) {
        ProblemReporter.info(message, e);
    }


    public static ParseResult containsLine(String test, IFile file) throws IOException {
        String packageName = null;
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file.getRawLocation().toFile()), "UTF-8"));
        try {
            String line = null;
            while ((line = r.readLine()) != null) {
                if (packageName == null && line.startsWith("package ")) {
                    packageName = line.substring(8).replace(";", "").trim();
                }

                if (line.contains(test)) {
                    log(line, null);
                    int start = line.indexOf("(", line.indexOf(test));
                    int end = line.indexOf(")");
                    String paramName = line.substring(start, end);
                    start = line.indexOf("\"") + 1;
                    end = line.lastIndexOf("\"");
                    paramName = line.substring(start, end);

                    return new ParseResult(packageName != null ? packageName : "", paramName, line);
                }
            }
        } finally {
            r.close();
        }
        return null;
    }

    public static Annotation addAnnotation(EclipseNode node, String annotationString) throws SimpleAnnotationParserException {
        SimpleAnnotationParser.Anno parsedAnnotation = SimpleAnnotationParser.parseEntry(annotationString, node);

        return addAnnotationFromMetadata(parsedAnnotation, node);
    }

    public static Annotation addAnnotationFromMetadata(SimpleAnnotationParser.Anno anno, EclipseNode node) {
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

    private static Expression convertMetadataValue(SimpleAnnotationParser.Value valueMetadata, EclipseNode node) {
        switch (valueMetadata.getType()) {
            case ARRAY: {
                ArrayList<Expression> list = new ArrayList<Expression>();
                for (SimpleAnnotationParser.Value next : valueMetadata.getArrayValues()) {
                    list.add(convertMetadataValue(next, node));
                }
                return array(node, list.toArray(new Expression[list.size()]));
            }
            case FIELD: {
                //  String typeName = valueMetadata.getField().substring(0, valueMetadata.getField().lastIndexOf("."));
                //  String fieldName = valueMetadata.getField().substring(valueMetadata.getField().lastIndexOf(".")+1);
                return fieldAccess(valueMetadata.getField(), node);
            }
            case LITERAL:
                Object literalValue = valueMetadata.getLiteral();
                //first tryout: string only
                if (literalValue instanceof Boolean) {
                    return ((Boolean) literalValue).booleanValue() ? new TrueLiteral(0, 0) : new FalseLiteral(0, 0);
                } else if (literalValue instanceof Number) {
                    if (literalValue instanceof Integer) {
                        return IntLiteral.buildIntLiteral(literalValue.toString().toCharArray(), 0, 0);
                    }
                    //TODO
                }
                return new StringLiteral(((String) literalValue).toCharArray(), 0, 0, 0);

            //          MemberValuePair mvp = new MemberValuePair(JUSTIFICATION, 0, 0, new StringLiteral(GENERATED_CODE, 0, 0, 0));
            //          anns = addAnnotation(source, anns, EDU_UMD_CS_FINDBUGS_ANNOTATIONS_SUPPRESSFBWARNINGS, mvp);

            //          return node.getTreeMaker().Literal(literalValue);
            default:
                throw new RuntimeException("unknown type " + valueMetadata.getType());
        }
    }


    /**
     * adds an annotation to the given field. if the annotation already exists no change will happen.
     * adapted from method EclipseHandlerUtil.addSuppressWarningsAll
     */
    public static Annotation addAnnotation(EclipseNode node, String annotationClassName, Map<String, Object> parameters) {

        //from EclipseHandlerUtil:
        //     Annotation[] anns = addAnnotation(source, originalAnnotationArray, TypeConstants.JAVA_LANG_SUPPRESSWARNINGS, new StringLiteral(ALL, 0, 0, 0));
        //     field.annotations = addSuppressWarningsAll(type, field, field.annotations);

        Annotation[] originalAnnotationArray = null;
        if (node.get() instanceof TypeDeclaration) {
            originalAnnotationArray = ((TypeDeclaration) node.get()).annotations;
        } else {
            FieldDeclaration fieldDecl = (FieldDeclaration) node.get();
            originalAnnotationArray = fieldDecl.annotations;
        }

        //Annotation[] modifiedAnnoationArray =  EclipseHandlerUtil.addAnnotation(node.get(), originalAnnotationArray, Eclipse.fromQualifiedName(annotationClassName), null);
        Annotation[] modifiedAnnoationArray = addAnnotation(node.get(), originalAnnotationArray, Eclipse.fromQualifiedName(annotationClassName), parameters);
        if (node.get() instanceof TypeDeclaration) {
            ((TypeDeclaration) node.get()).annotations = modifiedAnnoationArray;
        } else {
            FieldDeclaration fieldDecl = (FieldDeclaration) node.get();
            fieldDecl.annotations = modifiedAnnoationArray;
        }

        return modifiedAnnoationArray[modifiedAnnoationArray.length - 1];
    }

    /**
     * adds an annotation to the given field
     * adapted from method JavacHandlerUtil.addSuppressWarningsAll
     */

    private static Annotation[] addAnnotation(ASTNode source, Annotation[] originalAnnotationArray, char[][] annotationTypeFqn, Map<String, Object> parameters) {

        ASTNode arg = parameters.isEmpty() ? null : (ASTNode) parameters.values().iterator().next();

        char[] simpleName = annotationTypeFqn[annotationTypeFqn.length - 1];

        if (originalAnnotationArray != null) for (Annotation ann : originalAnnotationArray) {
            if (ann.type instanceof QualifiedTypeReference) {
                char[][] t = ((QualifiedTypeReference) ann.type).tokens;
                if (Arrays.deepEquals(t, annotationTypeFqn)) return originalAnnotationArray;
            }

            if (ann.type instanceof SingleTypeReference) {
                char[] lastToken = ((SingleTypeReference) ann.type).token;
                if (Arrays.equals(lastToken, simpleName)) return originalAnnotationArray;
            }
        }

        int pS = source.sourceStart, pE = source.sourceEnd;
        long p = (long) pS << 32 | pE;
        long[] poss = new long[annotationTypeFqn.length];
        Arrays.fill(poss, p);
        QualifiedTypeReference qualifiedType = new QualifiedTypeReference(annotationTypeFqn, poss);
        setGeneratedBy(qualifiedType, source);
        Annotation ann;
        if (parameters.isEmpty()) {
            MarkerAnnotation ma = new MarkerAnnotation(qualifiedType, pS);
            ma.declarationSourceEnd = pE;
            ann = ma;
        } else {
            NormalAnnotation na = new NormalAnnotation(qualifiedType, pS);
            na.declarationSourceEnd = pE;
            arg.sourceStart = pS;
            arg.sourceEnd = pE;

            List<MemberValuePair> pairs = new ArrayList<MemberValuePair>();
            for (Map.Entry<String, Object> nextEntry : parameters.entrySet()) {
                MemberValuePair mvp = new MemberValuePair(nextEntry.getKey().toCharArray(), 0, 0, (Expression) nextEntry.getValue());
                setGeneratedBy(mvp, source);
                setGeneratedBy(mvp.value, source);
                mvp.value.sourceStart = pS;
                mvp.value.sourceEnd = pE;
                pairs.add(mvp);
            }
            na.memberValuePairs = pairs.toArray(new MemberValuePair[pairs.size()]);
            ann = na;
        }
        setGeneratedBy(ann, source);
        if (originalAnnotationArray == null) return new Annotation[]{ann};
        Annotation[] newAnnotationArray = new Annotation[originalAnnotationArray.length + 1];
        System.arraycopy(originalAnnotationArray, 0, newAnnotationArray, 0, originalAnnotationArray.length);
        newAnnotationArray[originalAnnotationArray.length] = ann;
        return newAnnotationArray;
    }


    public static QualifiedNameReference fieldAccess(String fqName, EclipseNode node) {
        int p1 = node.get().sourceStart - 1;
        int p2 = node.get().sourceStart - 2;
        long pos = (((long) p1) << 32) | p2;
        return new QualifiedNameReference(Eclipse.fromQualifiedName(fqName), new long[]{pos, pos}, p1, p2);
    }

    public static ArrayInitializer array(EclipseNode node, Expression... expr) {
        ArrayInitializer array = new ArrayInitializer();
        array.expressions = expr;
        return array;
    }

}
