package jsoft.plugserver.sdk

package object api {

  trait Category

  case object ServiceCategory extends Category{
    override def toString: String = "Service"
  }

  case object RestServiceCategory extends Category{
    override def toString: String = "RestService"
  }

  case class CustomCategory(name: String) extends Category{
    override def toString: String = name
  }

}
