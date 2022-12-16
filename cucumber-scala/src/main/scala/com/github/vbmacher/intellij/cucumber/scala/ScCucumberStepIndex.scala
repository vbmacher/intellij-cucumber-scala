package com.github.vbmacher.intellij.cucumber.scala

import com.github.vbmacher.intellij.cucumber.scala.ScCucumberStepIndex.INDEX_ID
import com.intellij.lang.{LighterAST, LighterASTNode}
import com.intellij.openapi.util.io.DataInputOutputUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.tree.RecursiveLighterASTNodeWalkingVisitor
import com.intellij.util.indexing._
import com.intellij.util.io.{DataExternalizer, InlineKeyDescriptor, KeyDescriptor}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType

import java.io.{DataInput, DataOutput}
import java.util
import scala.jdk.CollectionConverters._

class ScCucumberStepIndex extends FileBasedIndexExtension[Boolean, Seq[Int]] {
  private final val STEP_KEYWORDS = Seq(
    "Әмма", "Нәтиҗәдә", "Вә", "Әйтик", "Һәм", "Ләкин", "Әгәр", "Und", "Angenommen", "Gegeben seien", "Dann", "Aber",
    "Wenn", "Gegeben sei", "यदि", "तदा", "अगर", "और", "कदा", "परन्तु", "चूंकि", "जब", "किन्तु", "तथा", "पर", "तब", "Dados", "Entao",
    "Dada", "Então", "Mas", "Dadas", "Dado", "Quando", "E", "Bet", "Ir", "Tada", "Kai", "Duota", "awer", "a", "an",
    "wann", "mä", "ugeholl", "dann", "I", "Kada", "Kad", "Zadan", "Ali", "Onda", "Zadano", "Zadani", "Bet", "Kad",
    "Tad", "Ja", "Un", "E", "Sipoze ke", "Sipoze", "Epi", "Men", "Le sa a", "Le", "Ak", "Lè", "Sipoze Ke", "Lè sa a",
    "Ha", "Adott", "De", "Amikor", "És", "Majd", "Akkor", "Amennyiben", "并且", "而且", "假如", "同时", "当", "假设", "那么",
    "假定", "但是", "Нехай", "Якщо", "І", "Припустимо, що", "Дано", "Припустимо", "Коли", "Та", "Але", "То", "Тоді",
    "А також", "It's just unbelievable", "Yeah nah", "Too right", "But at the end of the day I reckon", "Y'know",
    "Maka", "Tapi", "Ketika", "Dengan", "Dan", "اگر", "تب", "اور", "جب", "بالفرض", "فرض کیا", "پھر", "لیکن", "Maar",
    "En", "Dan", "Wanneer", "Gegewe", "Бирок", "Аммо", "Унда", "Ва", "Лекин", "Агар", "Δεδομένου", "Τότε", "Και", "Αλλά",
    "Όταν", "Aye", "Let go and haul", "Gangway!", "Avast!", "Blimey!", "When", "Then", "Given", "But", "And", "Kaj", "Do",
    "Se", "Sed", "Donitaĵo", "Ef", "Þegar", "Þá", "En", "Og", "Quando", "E", "Allora", "Dato", "Dati", "Date", "Data",
    "Ma", "Cuando", "Dada", "Pero", "Entonces", "Dados", "Y", "Dadas", "Dado", "Kui", "Ja", "Eeldades", "Kuid", "Siis",
    "اذاً", "لكن", "و", "متى", "بفرض", "ثم", "عندما", "Thì", "Khi", "Biết", "Và", "Cho", "Nhưng", "もし", "かつ", "但し",
    "ただし", "しかし", "ならば", "前提", "A", "Anrhegedig a", "Pryd", "Yna", "Ond", "هنگامی", "با فرض", "آنگاه", "اما", "و",
    "Dat fiind", "Dati fiind", "Atunci", "Dați fiind", "Dar", "Si", "Când", "Daţi fiind", "Și", "Cand", "Şi", "Date fiind",
    "Als", "Maar", "Gegeven", "En", "Wanneer", "Stel", "Dan", "Gitt", "Så", "Når", "Men", "Og", "Mutta", "Ja", "Oletetaan",
    "Kun", "Niin", "Пусть", "Допустим", "К тому же", "То", "Дано", "Когда", "Но", "Тогда", "Если", "И", "А", "Также",
    "Дадено", "И", "То", "Когато", "Но", "Maka", "Apabila", "Tapi", "Kemudian", "Dan", "Tetapi", "Diberi", "Bagi",
    "Etant donnés", "Alors", "Étant données", "Etant donné", "Étant donnée", "Lorsqu'", "Etant donnée", "Et", "Étant donné",
    "Quand", "Lorsque", "Mais", "Soit", "Etant données", "Étant donnés", "Njuk", "Tapi", "Menawa", "Nalika", "Ananging",
    "Lan", "Nanging", "Manawa", "Nalikaning", "Banjur", "Givun", "Youse know when youse got", "Youse know like when",
    "An", "Den youse gotta", "Buh", "Dun", "Wun", "WEN", "I CAN HAZ", "BUT", "AN", "DEN", "Potom", "Za predpokladu",
    "Tak", "Pokiaľ", "A zároveň", "A", "Ak", "A taktiež", "Ale", "Keď", "A tiež", "Privzeto", "Ampak", "Takrat", "Ko",
    "Nato", "Zaradi", "Ce", "Potem", "Če", "Ter", "Kadar", "Toda", "Dano", "Podano", "Vendar", "In", "I", "Atesa",
    "Donada", "Aleshores", "Cal", "Però", "Donat", "Quan", "Atès", "ನೀಡಿದ", "ಮತ್ತು", "ಆದರೆ", "ನಂತರ", "ಸ್ಥಿತಿಯನ್ನು", "Så", "När",
    "Och", "Men", "Givet", "그러면", "만약", "먼저", "조건", "단", "만일", "하지만", "그리고", "Mais", "E", "Dada", "Pero",
    "Dados", "Logo", "Cando", "Dadas", "Dado", "Entón", "那麼", "假如", "而且", "同時", "假設", "當", "假定", "但是", "並且",
    "And y'all", "But y'all", "When y'all", "Then y'all", "Given y'all", "Zadate", "I", "Kad", "Zatati", "Ali",
    "Kada", "Onda", "Zadato", "Pak", "A", "Ale", "A také", "Když", "Za předpokladu", "Pokud", "ਅਤੇ", "ਜਿਵੇਂ ਕਿ", "ਪਰ",
    "ਜੇਕਰ", "ਜਦੋਂ", "ਤਦ", "Задато", "Кад", "Задати", "Када", "Задате", "Али", "Онда", "И", "ghu' noblu'", "DaH ghu' bejlu'",
    "latlh", "qaSDI'", "'ach", "'ej", "'a", "vaj", "กำหนดให้", "แต่", "และ", "เมื่อ", "ดังนั้น", "మరియు", "అప్పుడు", "ఈ పరిస్థితిలో",
    "చెప్పబడినది", "కాని", "I", "Gdy", "Kiedy", "Wtedy", "Ale", "Jeżeli", "Jeśli", "Mając", "Zakładając", "Oraz", "Og", "Så",
    "Når", "Men", "Givet", "כאשר", "וגם", "אז", "בהינתן", "אבל", "אזי", "Ond", "Ðurh", "Ða", "Ða ðe", "Ac", "Thurh", "Þa",
    "7", "Þa þe", "Tha", "Þurh", "Tha the", "Ama", "Fakat", "O zaman", "Ve", "Eğer ki", "Diyelim ki"
  )

  val getName: ID[Boolean, Seq[Int]] = INDEX_ID

  val getVersion = 6

  val getInputFilter: FileBasedIndex.InputFilter = new DefaultFileTypeSpecificInputFilter(ScalaFileType.INSTANCE) {
    override def acceptInput(file: VirtualFile): Boolean = {
      super.acceptInput(file)
    }
  }

  override def dependsOnFileContent(): Boolean = true

  val getIndexer: DataIndexer[Boolean, Seq[Int], FileContent] = {
    inputData => {
      val text = inputData.getContentAsText
      val lighterAst = inputData.asInstanceOf[PsiDependentFileContent].getLighterAST
      val result = this.getStepDefinitionOffsets(lighterAst, text);

      Map(true -> result).asJava
    }
  }

  override val getKeyDescriptor: KeyDescriptor[Boolean] = new InlineKeyDescriptor[Boolean] {
    override def fromInt(n: Int): Boolean = n != 0

    override def toInt(t: Boolean): Int = if (t) 1 else 0

    override def isCompactFormat: Boolean = true
  }


  val getValueExternalizer: DataExternalizer[Seq[Int]] = new DataExternalizer[Seq[Int]]() {
    override def save(out: DataOutput, value: Seq[Int]): Unit = {
      DataInputOutputUtilRt.writeSeq[Int](out, value.asJava, descriptor => {
        DataInputOutputUtilRt.writeINT(out, descriptor)
      })
    }

    override def read(in: DataInput): Seq[Int] = {
      DataInputOutputUtilRt.readSeq(in, () => DataInputOutputUtilRt.readINT(in)).asScala.toSeq
    }
  }

  private def getStepDefinitionOffsets(lighterAst: LighterAST, text: CharSequence): Seq[Int] = {
    val result = new util.ArrayList[Int]()

    val visitor = new RecursiveLighterASTNodeWalkingVisitor(lighterAst) {
      override def visitNode(element: LighterASTNode): Unit = {
        if (element.getTokenType == ScalaElementType.METHOD_CALL) {
          val methodAndArguments = lighterAst.getChildren(element)
          if (methodAndArguments.size() < 2) {
            super.visitNode(element)
            return
          }

          val gherkinMethod = methodAndArguments.get(0)
          if (gherkinMethod != null && isStepDefinitionCall(gherkinMethod, text)) {
            val expression = methodAndArguments.get(1)
            if (expression.getTokenType == ScalaElementType.ARG_EXPRS) {
              result.add(element.getStartOffset)
            }
          }
        }
        super.visitNode(element)
      }
    }
    visitor.visitNode(lighterAst.getRoot)
    result.asScala.toSeq
  }


  private def isStepDefinitionCall(methodName: LighterASTNode, text: CharSequence) = {
    STEP_KEYWORDS.contains(text.subSequence(methodName.getStartOffset, methodName.getEndOffset).toString())
  }


}

object ScCucumberStepIndex {
  final val INDEX_ID: ID[Boolean, Seq[Int]] = ID.create("scala.cucumber.step")
}