package com.github.vbmacher.intellij.cucumber.scala;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.cucumber.CucumberStepIndex;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScCucumberStepIndex extends CucumberStepIndex {
    private final static String[] PACKAGES = new String[ScCucumberUtil.CUCUMBER_PACKAGES().length()];

    public final static ID<Boolean, List<Integer>> INDEX_ID = ID.create("scala.cucumber.step");

    static {
        ScCucumberUtil.CUCUMBER_PACKAGES().copyToArray(PACKAGES);
    }

    private final FileBasedIndex.InputFilter inputFilter = new DefaultFileTypeSpecificInputFilter(ScalaFileType.INSTANCE) {
        @Override
        public boolean acceptInput(@NotNull VirtualFile file) {
            return super.acceptInput(file);
        }
    };

    @NotNull
    @Override
    public ID<Boolean, List<Integer>> getName() {
        return INDEX_ID;
    }

    @Override
    public int getVersion() {
        return 6;
    }

    @Override
    protected String[] getPackagesToScan() {
        return PACKAGES;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return inputFilter;
    }

    @Override
    public @NotNull DataIndexer<Boolean, List<Integer>, FileContent> getIndexer() {
        // NOTE: Had to override, because indirect step definitions weren't working. Classes/traits which don't directly
        // include the getPackagesToScan() package are ignored by the original method.
        return (inputData) -> {
            CharSequence text = inputData.getContentAsText();
            LighterAST lighterAst = ((PsiDependentFileContent) inputData).getLighterAST();
            List<Integer> result = this.getStepDefinitionOffsets(lighterAst, text);
            Map<Boolean, List<Integer>> resultMap = new HashMap<>();
            resultMap.put(true, result);
            return resultMap;
        };
    }

    @Override
    protected List<Integer> getStepDefinitionOffsets(@NotNull LighterAST lighterAst, @NotNull CharSequence text) {
        List<Integer> result = new ArrayList<>();

        RecursiveLighterASTNodeWalkingVisitor visitor = new RecursiveLighterASTNodeWalkingVisitor(lighterAst) {
            @Override
            public void visitNode(@NotNull LighterASTNode element) {
                if (element.getTokenType() == ScalaElementType.METHOD_CALL()) {
                    List<LighterASTNode> methodAndArguments = lighterAst.getChildren(element);

                    if (methodAndArguments.size() < 2) {
                        super.visitNode(element);
                        return;
                    }

                    LighterASTNode gherkinMethod = methodAndArguments.get(0);

                    if (gherkinMethod != null && isStepDefinitionCall(gherkinMethod, text)) {
                        LighterASTNode expression = methodAndArguments.get(1);
                        if (expression.getTokenType() == ScalaElementType.ARG_EXPRS()) {
                            result.add(element.getStartOffset());
                        }
                    }
                }
                super.visitNode(element);
            }
        };
        visitor.visitNode(lighterAst.getRoot());

        return result;
    }
}