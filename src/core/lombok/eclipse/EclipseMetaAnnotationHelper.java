package lombok.eclipse;

import lombok.core.MetaAnnotationProcessor;
import lombok.core.debug.ProblemReporter;
import lombok.experimental.MetaAnnotation;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class EclipseMetaAnnotationHelper {
    private EclipseMetaAnnotationHelper(){
        //no instances needed
    }

    public static Set<String> getAllMetaAnnotations() {
        Set<String> result = MetaAnnotationProcessor.getAllMetaAnnotationsFromSystemProperty();
        try {
            for (IProject nextProject : findAllOpenJavaProjects()) {
                    for (IFile nextFile : collectAllJavaFiles(nextProject)) {
                        String packageName = null;
                        if ((packageName = containsLine(MetaAnnotation.class.getName(), nextFile)) != null) {
                            StringBuilder typeName = new StringBuilder(packageName);
                            if (typeName.length() > 0) {
                                typeName.append(".");
                            }
                            typeName.append(nextFile.getName().replace("." + nextFile.getFileExtension(), ""));
                            result.add(typeName.toString());

                        }
                    }
            }
        } catch (CoreException e) {
            log(e.getMessage(), e);
        }
        catch (IOException e) {
            log(e.getMessage(), e);
        }
        return result;

    }

    private static List<IProject> findAllOpenJavaProjects() throws CoreException{
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
            } else if (member instanceof IFile && member.getFileExtension().equals("java")) {
                result.add((IFile) member);
            }
        }
    }

    private static void log(String message, Throwable e) {
        ProblemReporter.error(message, e);
    }


    public static String containsLine(String test, IFile file) throws IOException {
        String packageName = null;
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file.getRawLocation().toFile()), "UTF-8"));
        try {
            String line = null;
            while ((line = r.readLine()) != null) {
                if (packageName == null && line.startsWith("package ")) {
                    packageName = line.substring(8).replace(";", "").trim();
                }

                if (line.contains(test)) {
                    return packageName != null ? packageName : "";
                }
            }
        } finally {
            r.close();
        }
        return null;
    }
}
