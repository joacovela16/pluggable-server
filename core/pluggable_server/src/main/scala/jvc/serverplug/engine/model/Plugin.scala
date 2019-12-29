package jvc.serverplug.engine.model

import java.net.URL
import java.util.ServiceLoader

import akka.http.scaladsl.model.DateTime
import jvc.prototype.pluggable.sdk.Registry
import jvc.serverplug.engine.model.Types.{PluginID, RegistryID}
import org.apache.xbean.classloader.JarFileClassLoader

import scala.collection.parallel.mutable

sealed trait Plugin {
  def id: PluginID
}

case class PluginInstalled(id: PluginID, installedDate: DateTime, urls: Array[URL], active: Boolean) extends Plugin {

  lazy val classLoader: JarFileClassLoader = new JarFileClassLoader(System.nanoTime().toString, urls, this.getClass.getClassLoader)
  lazy val srv: ServiceLoader[Registry] = ServiceLoader.load(classOf[Registry], classLoader)
  lazy val registry: mutable.ParMap[RegistryID, Registry] = {

    val tmpMap: mutable.ParMap[RegistryID, Registry] = mutable.ParMap.empty

    srv.forEach(x => tmpMap + (x.identifier, x))

    tmpMap
  }
}

case class PluginUninstalled(id: PluginID) extends Plugin