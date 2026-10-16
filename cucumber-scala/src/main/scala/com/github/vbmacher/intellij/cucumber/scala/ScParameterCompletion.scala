package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.CucumberUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

class ScParameterCompletion extends CompletionContributor {
  private def parameterStart(file: PsiFile, offset: Int): Option[Int] = {
    for {
      leaf <- Option(file.findElementAt(math.max(0, offset - 1)))
      literal <- Option(PsiTreeUtil.getParentOfType(leaf, classOf[ScLiteral], false))
      call <- callOfPattern(literal)
      (_, expression) <- call.definition
      if CucumberUtil.isCucumberExpression(expression)
      before = file.getText.substring(literal.getTextOffset, offset)
      start = before.lastIndexOf('{')
      if start >= 0 && before.lastIndexOf('}') < start
      if before.take(start).reverse.takeWhile(_ == '\\').length % 2 == 0
    } yield literal.getTextOffset + start + 1
  }

  override def beforeCompletion(context: CompletionInitializationContext): Unit =
    parameterStart(context.getFile, context.getStartOffset).foreach { start =>
      context.getOffsetMap.addOffset(CompletionInitializationContext.START_OFFSET, start)
    }

  override def fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet): Unit = {
    val file = parameters.getOriginalFile
    for {
      start <- parameterStart(file, parameters.getOffset)
      module <- Option(ModuleUtilCore.findModuleForPsiElement(file))
    } {
      val target = result.withPrefixMatcher(file.getText.substring(start, parameters.getOffset))
      val registrations = indexedCalls(file.getProject, stepSearchScope(module), kind = ScCucumberStepIndex.Parameters)
        .flatMap(call => call.parameterType.map { case (name, regex) => (name, regex, call) })
      registrations.sortBy(r => (r._1, r._3.getContainingFile.getName, r._3.getTextOffset)).foreach { case (name, regex, call) =>
        target.addElement(LookupElementBuilder.create(call, name).withTypeText(call.getContainingFile.getName)
          .withTailText(" · " + regex, true).withInsertHandler((context: InsertionContext, _: com.intellij.codeInsight.lookup.LookupElement) => insertName(context, start, name)))
      }
      ScStepDefinition.BuiltInParameterTypes.keys.toSeq.filter(_.nonEmpty).sorted.foreach { name =>
        target.addElement(LookupElementBuilder.create(name).withTypeText("Cucumber built-in")
          .withInsertHandler((context: InsertionContext, _: com.intellij.codeInsight.lookup.LookupElement) => insertName(context, start, name)))
      }
      result.stopHere()
    }
  }

  private def insertName(context: InsertionContext, start: Int, name: String): Unit = {
    context.getDocument.replaceString(start, context.getTailOffset, name)
    context.setTailOffset(start + name.length)
    context.getEditor.getCaretModel.moveToOffset(context.getTailOffset)
    val end = context.getTailOffset
    if (end >= context.getDocument.getTextLength || context.getDocument.getCharsSequence.charAt(end) != '}')
      context.getDocument.insertString(end, "}")
  }
}
