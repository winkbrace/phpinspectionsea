package com.kalessil.phpStorm.phpInspectionsEA.inspectors.codeStyle;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.Function;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.Parameter;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.GenericPhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.settings.StrictnessCategory;
import com.kalessil.phpStorm.phpInspectionsEA.utils.OpenapiResolveUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.PhpLanguageUtil;
import com.kalessil.phpStorm.phpInspectionsEA.utils.Types;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/*
 * This file is part of the Php Inspections (EA Extended) package.
 *
 * (c) Vladimir Reznichenko <kalessil@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

public class ParameterDefaultValueIsNotNullInspector extends PhpInspection {
    private static final String message = "Null should be used as the default value (nullable types are the goal, right?)";

    @NotNull
    public String getShortName() {
        return "ParameterDefaultValueIsNotNullInspection";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new GenericPhpElementVisitor() {
            @Override
            public void visitPhpMethod(@NotNull Method method) {
                if (this.shouldSkipAnalysis(method, StrictnessCategory.STRICTNESS_CATEGORY_CODE_STYLE)) { return; }

                this.analyze(method);
            }

            @Override
            public void visitPhpFunction(@NotNull Function function) {
                if (this.shouldSkipAnalysis(function, StrictnessCategory.STRICTNESS_CATEGORY_CODE_STYLE)) { return; }

                this.analyze(function);
            }

            private void analyze(@NotNull Function function) {
                final Parameter[] arguments = function.getParameters();
                if (arguments.length > 0) {
                    /* collect violations */
                    final List<Parameter> violations = new ArrayList<>();
                    for (final Parameter argument : arguments) {
                        final PsiElement defaultValue = argument.getDefaultValue();
                        if (defaultValue != null && !PhpLanguageUtil.isNull(defaultValue)) {
                            /* false-positives: null can not be used due to implicit type hints */
                            final PhpType declared = argument.getDeclaredType();
                            if (declared.isEmpty() || declared.getTypes().stream().anyMatch(t -> Types.getType(t).equals(Types.strNull))) {
                                violations.add(argument);
                            }
                        }
                    }

                    if (!violations.isEmpty()) {
                        /* false-positives: methods overrides, so violation should be addressed in the parent */
                        if (function instanceof Method) {
                            final PhpClass clazz = ((Method) function).getContainingClass();
                            if (clazz != null) {
                                final PhpClass parent = OpenapiResolveUtil.resolveSuperClass(clazz);
                                if (parent != null) {
                                    final Method parentMethod = OpenapiResolveUtil.resolveMethod(parent, function.getName());
                                    if (parentMethod != null && !parentMethod.getAccess().isPrivate()) {
                                        violations.clear();
                                        return;
                                    }
                                }
                            }
                        }

                        /* report violations */
                        violations.forEach(param -> holder.registerProblem(param, message));
                        violations.clear();
                    }
                }
            }
        };
    }
}
