package jvc.serverplug.engine

import java.net.{URL, URLClassLoader}
import java.util.ServiceLoader

import akka.http.scaladsl.model.DateTime
import jvc.prototype.pluggable.sdk.Registry
import org.apache.xbean.classloader.JarFileClassLoader

import scala.collection.mutable

package object plugin {

  class CustomClassLoader(urls: Seq[URL], parent: ClassLoader) extends URLClassLoader(urls.toArray, parent) {
    override def close(): Unit = {
      super.close()
    }
  }

  sealed trait Plugin {
    def id: PluginID
  }

  case class PluginInstalled(id: PluginID, installedDate: DateTime, urls: Array[URL], active: Boolean) extends Plugin {

    lazy val classLoader: JarFileClassLoader = new JarFileClassLoader(System.nanoTime().toString, urls, this.getClass.getClassLoader)

    lazy val srv: ServiceLoader[Registry] = ServiceLoader.load(classOf[Registry], classLoader)
    lazy val registry: Map[RegistryID, Registry] = {

      val tmpMap: mutable.Map[RegistryID, Registry] = mutable.Map.empty

      srv.forEach { x =>
        tmpMap.put(x.identifier, x)
      }

      if (active) tmpMap.values.foreach { r => r.onStart() }

      tmpMap.toMap
    }
  }

  case class PluginUninstalled(id: PluginID) extends Plugin

}
