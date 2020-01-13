package jsoft.plugserver.engine.model

import java.net.URL
import java.util.ServiceLoader

import akka.http.scaladsl.model.DateTime
import jsoft.plugserver.engine.model.Types.{PluginID, ServiceID}
import jsoft.plugserver.sdk.api.{Service, Category}
import org.apache.xbean.classloader.JarFileClassLoader

import scala.collection.parallel.mutable

sealed trait Plugin {

  private val flags: mutable.ParHashSet[String] = mutable.ParHashSet.empty

  def addFlag(x: String): this.type = {
    flags += x
    this
  }

  def removeFlag(x: String): this.type = {
    flags -= x
    this
  }

  def hasFlag(x: String): Boolean = flags.contains(x)

  def id: PluginID
}

case class ServiceProxy(registry: Service, active: Boolean = true, category: Category, description: String)

case class PluginInstalled(id: PluginID, installedDate: DateTime, urls: Array[URL]) extends Plugin {

  lazy val classLoader: JarFileClassLoader = new JarFileClassLoader(System.nanoTime().toString, urls, this.getClass.getClassLoader)
  lazy val srv: ServiceLoader[Service] = ServiceLoader.load(classOf[Service], classLoader)
  lazy val registry: mutable.ParMap[ServiceID, ServiceProxy] = {
    val tmpMap: mutable.ParMap[ServiceID, ServiceProxy] = mutable.ParMap.empty

    srv.forEach(x => tmpMap + (x.identifier, ServiceProxy(x, active = true, x.category, x.description)))

    tmpMap
  }

  def isActive: Boolean = hasFlag("active")

  def asActive(): PluginInstalled = addFlag("active")

  def asInactive(): PluginInstalled = removeFlag("active")

  def setActive(b:Boolean): PluginInstalled = if (b) asActive() else asInactive()
}

case class PluginUninstalled(id: PluginID) extends Plugin