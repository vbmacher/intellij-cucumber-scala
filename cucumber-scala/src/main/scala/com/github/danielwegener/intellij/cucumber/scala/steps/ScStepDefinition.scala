package com.github.danielwegener.intellij.cucumber.scala.steps

import java.util

import com.github.danielwegener.intellij.cucumber.scala.ScCucumberUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.cucumber.steps.AbstractStepDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFunctionExpr, ScMethodCall}

import scala.collection.JavaConverters._
import scala.util.Try

class ScStepDefinition(scMethod: ScMethodCall) extends AbstractStepDefinition(scMethod.getFirstChild) {

  override def getVariableNames: util.List[String] = Try {
    // WHEN("""regexp""") { (arg0:Int, arg1:String) <-- we want to match these

    val params = for {
      block <- scMethod.args.exprs.collectFirst({ case b: ScBlockExpr => b })
      function <- block.getChildren.collectFirst({ case f: ScFunctionExpr => f })

      parameters = function.parameters
    } yield parameters.map(_.getName())

    seqAsJavaList(params.getOrElse(Seq.empty))
  }.getOrElse(seqAsJavaList(Seq.empty))

  @Nullable
  override def getCucumberRegexFromElement(element: PsiElement): String = Try {
    scMethod match {
      case mc: ScMethodCall => ScCucumberUtil.getStepRegex(mc).orNull
      case _ => null
    }
  }.getOrElse(null)

}

object ScStepDefinition {
  val LOG: Logger = Logger.getInstance(classOf[ScStepDefinition])

  def apply(scMethodCall: ScMethodCall): ScStepDefinition = new ScStepDefinition(scMethodCall)
}