package jvc.serverplug

import java.io.{File, FilenameFilter}
import java.net.{JarURLConnection, URL}
import java.nio.file._
import java.util.jar.JarFile

import akka.http.scaladsl.model.DateTime
import com.typesafe.scalalogging.LazyLogging
import jvc.prototype.pluggable.sdk.{Registry, RestRegistry}
import jvc.serverplug.engine.plugin.{Plugin, PluginInstalled, PluginUninstalled}
import jvc.serverplug.engine.rest.{PluginProxy, PluginState}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}

package object engine extends LazyLogging {

  type PluginID = String
  type RegistryID = String

  val WATCH_SERVICES: WatchService = FileSystems.getDefault.newWatchService()
  val PLUGIN_PATH: String = sys.props.getOrElse("pluginPath", "plugins")
  val PLUGIN_STORE: mutable.Map[PluginID, Plugin] = mutable.Map.empty

  def getActiveService(pluginID: PluginID, registryID: RegistryID): Option[Registry] = {
    PLUGIN_STORE.get(pluginID).collect { case x: PluginInstalled => x }.filter(_.active).flatMap(_.registry.get(registryID))
  }

  def getActiveRestService(pluginID: PluginID, registryID: RegistryID): Option[RestRegistry] = getActiveService(pluginID, registryID).collect { case x: RestRegistry => x }

  def getPlugin(pluginID: PluginID): Option[PluginInstalled] = PLUGIN_STORE.get(pluginID).collect { case x: PluginInstalled => x }

  def taskAddEntry(pluginID: String, forceActive: Boolean = false): Unit = {

    Option(new File(s"$PLUGIN_PATH/$pluginID"))
      .flatMap { x =>

        x.listFiles() match {
          case Array(x, y) =>

            val (maybeJar, libs) = if (x.isFile) (x, y) else (y, x)

            if (maybeJar.getName.endsWith(".jar") && libs.isDirectory) {

              val url: URL = new URL(s"jar:file:${maybeJar.getParent}/${maybeJar.getName}!/")

              val libsAbsPath: PluginID = libs.getAbsolutePath
              val libsUrl: Array[URL] = libs.listFiles(new FilenameFilter {
                override def accept(dir: File, name: PluginID): Boolean = name.endsWith(".jar")
              }).map(x => x.toURI.toURL/*new URL(s"jar:file:$libsAbsPath/${x.getName}!/")*/)

              Some(libsUrl :+ maybeJar.toURI.toURL)
            } else {
              None
            }

          case _ => None
        }
      }
      .foreach(urls => PLUGIN_STORE.put(pluginID, PluginInstalled(pluginID, DateTime.now, urls, forceActive)))
  }

  def taskRemoveEntry(pluginId: PluginID, delete: Boolean = false): Unit = {
    PLUGIN_STORE.get(pluginId).collect { case x: PluginInstalled => x }.foreach { plugin =>

      if (delete) {
        PLUGIN_STORE -= pluginId
      } else {
        PLUGIN_STORE.put(pluginId, PluginUninstalled(pluginId))
      }

      plugin.registry.values.foreach { x =>
        Future(x.onDestroy()).onComplete {
          case Failure(exception) => logger.error(exception.getLocalizedMessage, exception)
          case _ =>
        }
      }

      Try {
        plugin.classLoader.close()
        plugin.classLoader.destroy()
      } match {
        case Failure(exception) => logger.error(exception.getLocalizedMessage, exception)
        case _ => System.gc()
      }
    }
  }

  def taskLoadCreated(): Unit = {
    val file: File = new File(PLUGIN_PATH)

    if (file.exists()) {
      file.listFiles().filter(_.isDirectory).foreach(x => taskAddEntry(x.getName, true))
    }
  }

  def taskWatchJars(): Unit = {
    new Thread {
      override def run() {

        Paths.get(PLUGIN_PATH).register(WATCH_SERVICES, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE)

        Try {
          while (true) {

            val key: WatchKey = WATCH_SERVICES.take()
            key.pollEvents().forEach { x =>

              val context: String = x.context().toString

              x.kind().toString match {
                case "ENTRY_CREATE" =>
                  taskAddEntry(context)
                case "ENTRY_DELETE" =>
                  taskRemoveEntry(context, true)
                case _ =>
              }
            }
          }
        }
      }
    }.start()
  }

  def buildState(xs: Iterable[Plugin]): PluginState = PluginState(
    xs.map {
      case x: PluginInstalled =>
        PluginProxy(x.id, Option(x.installedDate), true, x.active, x.registry.keys.toSeq)
      case PluginUninstalled(id) =>
        PluginProxy(id, None, false, false, Nil)
    }.toSeq
  )

  def taskStopWatch(): Unit = WATCH_SERVICES.close()
}
