package com.github.vbmacher.intellij.cucumber.scala.tables

import com.github.vbmacher.intellij.cucumber.scala._
import com.github.vbmacher.intellij.cucumber.scala.ScCucumber._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.cucumber.psi.GherkinTableCell
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnit4])
class DataTableMappingsSpec extends ScCucumberSpecBase {
  override val DESCRIPTOR = new LibraryLightProjectDescriptor(LocalDependency("scala-library",
    classOf[Option[_]].getProtectionDomain.getCodeSource.getLocation, classOf[Option[_]].getProtectionDomain.getCodeSource.getLocation))

  @Test
  def testExplicitAliasesResolveToConstructorFieldsAndUnknownHeadersAreBounded(): Unit = {
    myFixture.addFileToProject("java/lang/String.java", "package java.lang; public final class String {}")
    myFixture.addFileToProject("Rows.scala", """case class TransferRow(account: String, amount: Int)
      |class Rows {
      | DataTableType { (entry: Map[String, String]) => TransferRow(entry("owner"), entry("amount").toInt) }
      | Given("transfers") { (rows: Seq[TransferRow]) => () }
      |}
      |""".stripMargin)
    val file = myFixture.configureByText("rows.feature", "Feature: Rows\n  Scenario: Rows\n    Given transfers\n      | owner | amuont |\n      | Alice | 10 |\n")
    inReadAction {
      val cells = PsiTreeUtil.findChildrenOfType(file, classOf[GherkinTableCell]).asScala.toSeq
      val owner = cells.find(_.getText.trim == "owner").get
      val headers = DataTableMappings.headers(owner)
      headers.keySet shouldBe Set("owner", "amount")
      headers("owner").getName shouldBe "account"
      val reference = owner.getReferences.find(_.resolve() == headers("owner")).get
      reference.getRangeInElement.substring(owner.getText) shouldBe "owner"
      val typo = cells.find(_.getText.trim == "amuont").get
      DataTableMappings.headers(typo).keySet shouldBe Set("owner", "amount")
      DataTableMappings.headers(cells.find(_.getText.trim == "Alice").get) shouldBe empty
    }
  }

  @Test
  def testUnmappedTablesHaveNoHeaderReferencesOrDiagnostics(): Unit = {
    val file = myFixture.configureByText("unmapped.feature", "Feature: Rows\n  Scenario: Rows\n    Given unmapped\n      | anything |\n      | value |\n")
    inReadAction {
      PsiTreeUtil.findChildrenOfType(file, classOf[GherkinTableCell]).asScala.foreach(cell => DataTableMappings.headers(cell) shouldBe empty)
    }
  }
}
