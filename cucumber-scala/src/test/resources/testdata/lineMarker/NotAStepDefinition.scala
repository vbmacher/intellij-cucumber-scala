class NotAStepDefinition {

  ordinaryFunction("blahblah") {

  }

  def ordinaryFunction(str: String)(f: => Unit): Unit = ()
}
