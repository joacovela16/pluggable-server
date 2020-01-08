package jsoft.plugserver.engine.model

import java.net.URL
import java.util.ServiceLoader

import akka.http.scaladsl.model.DateTime
import jsoft.plugserver.engine.model.Types.{PluginID, ServiceID}
import jsoft.plugserver.sdk.Service
import org.apache.xbean.classloader.JarFileClassLoader

import scala.collection.parallel.mutable

sealed trait Plugin {
  def id: PluginID
}

case class ServiceProxy(registry: Service, active: Boolean = true)

case class PluginInstalled(id: PluginID, installedDate: DateTime, urls: Array[URL], active: Boolean) extends Plugin {

  lazy val classLoader: JarFileClassLoader = new JarFileClassLoader(System.nanoTime().toString, urls, this.getClass.getClassLoader)
  lazy val srv: ServiceLoader[Service] = ServiceLoader.load(classOf[Service], classLoader)
  lazy val registry: mutable.ParMap[ServiceID, ServiceProxy] = {

    val tmpMap: mutable.ParMap[ServiceID, ServiceProxy] = mutable.ParMap.empty

    srv.forEach(x => tmpMap + (x.identifier, ServiceProxy(x)))

    tmpMap
  }
}

case class PluginUninstalled(id: PluginID) extends Plugin