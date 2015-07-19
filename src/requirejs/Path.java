package requirejs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Path {
    protected PsiElement element;
    protected RequirejsProjectComponent component;
    protected String originValue;
    protected String path;
    protected String module = null;

    public Path(PsiElement element, RequirejsProjectComponent component) {
        this.element = element;
        this.component = component;

        parse(element.getText());
    }

    public void parse(String rawValue) {
        originValue = rawValue.replace("\"", "").replace("'", "");

        if (originValue.contains("!")) {
            String[] exclamationMarkSplit = originValue.split("!");
            if (exclamationMarkSplit.length == 2) {
                module = exclamationMarkSplit[0];
                path = exclamationMarkSplit[1];
            } else {
                module = exclamationMarkSplit[0];
                path = "";
            }
        } else {
            path = originValue;
        }
    }

    public String getOriginValue() {
        return originValue;
    }

    public String getPath() {
        return path;
    }

    public String getModule() {
        return module;
    }

    public boolean isAbsolutePath() {
        return path.startsWith("/");
    }

    public boolean isRelativePath() {
        return path.startsWith(".");
    }

    public boolean isBuildInVariable() {
        return originValue.equals("exports") || originValue.equals("module") || originValue.equals("require");
    }

    @Nullable
    public PsiElement resolve() {
        PsiElement result;

        if (this.isBuildInVariable()) {
            return getContainingFile();
        }

        if (this.isAbsolutePath()) {
            return resolveAbsolutePath();
        } else if (this.isRelativePath()) {
            result = probeResolveRelativePath();
            if (null != result) {
                return result;
            }
        }

        result = probeResolveWithBaseUrl();
        if (null != result) {
            return result;
        }

        result = probeResolveRequirePath();
        if (null != result) {
            return result;
        }

        result = probeResolveRequireAlias();
        if (null != result) {
            return result;
        }

        result = probeResolvePackage();
        if (null != result) {
            return result;
        }

        component.getLogger().debug("Could not resolve reference for " + this.getOriginValue());
        return null;
    }

    @Nullable
    protected PsiElement probeResolvePackage() {
        String packageName;
        String moduleId = null;
        if (this.getPath().indexOf('/') == -1) {
            packageName = this.getPath();
        } else {
            packageName = this.getPath().substring(0, this.getPath().indexOf('/'));
            moduleId = this.getPath().substring(this.getPath().indexOf('/') + 1);
        }
        for (Package pkg : component.packageConfig.packages) {
            if (pkg.name.equals(packageName)) {
                if (moduleId == null) {
                    moduleId = pkg.main;
                }
                VirtualFile targetFile = component.getBaseUrlPath(false)
                        .findFileByRelativePath(pkg.location + '/' + moduleId + ".js");
                if (null != targetFile) {
                    return getPsiManager().findFile(targetFile);
                }
            }
        }

        return null;
    }

    @Nullable
    protected PsiElement probeResolveRequireAlias() {
        String requireMapModule = FileUtils.removeExt(element.getContainingFile().getVirtualFile().getPath().replace(
                component.getWebDir(this.getElementFile()).getPath() + '/',
                ""
        ), ".js");

        RequirePathAlias alias = component.requireMap.getAliasByModule(requireMapModule, this.getPath());
        if (null != alias) {
            VirtualFile targetFile = FileUtils.findFileByPath(component.getWebDir(getElementFile()), alias.path);
            if (null != targetFile) {
                return getPsiManager().findFile(targetFile);
            }
        }

        return null;
    }

    @Nullable
    protected PsiElement probeResolveRequirePath() {
        VirtualFile targetFile = component.requirePaths.resolve(this.getPath());
        if (null != targetFile) {
            return getPsiManager().findFile(targetFile);
        }

        return null;
    }

    @NotNull
    protected PsiManager getPsiManager() {
        return PsiManager.getInstance(element.getProject());
    }

    @Nullable
    protected PsiElement probeResolveWithBaseUrl() {
        VirtualFile baseUrl = component.getBaseUrlPath(true);
        if (null != baseUrl) {
            VirtualFile targetFile = FileUtils.findFileByPath(baseUrl, this.getPath());

            if (targetFile != null) {
                return getPsiManager().findFile(targetFile);
            }
        }

        return null;
    }

    @Nullable
    protected PsiElement probeResolveRelativePath() {
        VirtualFile targetFile;
        PsiDirectory fileDirectory = element.getContainingFile().getContainingDirectory();
        if (null != fileDirectory) {
            targetFile = FileUtils.findFileByPath(fileDirectory.getVirtualFile(), this.getPath());
            if (null != targetFile) {
                return getPsiManager().findFile(targetFile);
            }
        }

        return null;
    }

    @Nullable
    protected PsiElement resolveAbsolutePath() {
        VirtualFile targetFile = FileUtils.findFileByPath(component.getWebDir(getElementFile()), this.getPath());
        if (null != targetFile) {
            return getPsiManager().findFile(targetFile);
        } else {
            return null;
        }
    }

    protected VirtualFile getElementFile() {
        return element
            .getContainingFile()
            .getOriginalFile()
            .getVirtualFile();
    }

    protected PsiElement getContainingFile() {
        return element.getContainingFile();
    }
}