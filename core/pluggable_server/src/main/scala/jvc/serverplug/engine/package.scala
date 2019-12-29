package jvc.serverplug

import java.io.{File, FileFilter, FilenameFilter}
import java.net.URL
import java.util.{Timer, TimerTask}

import akka.http.scaladsl.model.DateTime
import com.typesafe.scalalogging.LazyLogging
import jvc.prototype.pluggable.sdk.{Registry, RestRegistry}
import jvc.serverplug.engine.plugin.{Plugin, PluginInstalled, PluginUninstalled}
import jvc.serverplug.engine.rest.{PluginProxy, PluginState}

import scala.collection.parallel.ParSet
import scala.collection.parallel.mutable.ParMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}

package object engine extends LazyLogging {

  type PluginID = String
  type RegistryID = String

  val TIMER: Timer = new Timer()
  val PLUGIN_PATH: String = sys.props.getOrElse("pluginPath", "plugins")
  val PLUGIN_STORE: ParMap[PluginID, Plugin] = ParMap.empty

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

              val libsUrl: Array[URL] = libs.listFiles(new FilenameFilter {
                override def accept(dir: File, name: PluginID): Boolean = name.endsWith(".jar")
              }).map(x => x.toURI.toURL)

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
    PLUGIN_STORE.get(pluginId).foreach { x =>

      if (delete) {
        PLUGIN_STORE -= pluginId
      } else {
        PLUGIN_STORE.put(pluginId, PluginUninstalled(pluginId))
      }

      x match {
        case plugin: PluginInstalled =>

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

        case _ =>
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

    TIMER.scheduleAtFixedRate(
      new TimerTask {
        override def run(): Unit = {

          val file: File = new File(PLUGIN_PATH)
          val modules: Set[PluginID] = file
            .listFiles(new FileFilter {
              override def accept(pathname: File): Boolean = pathname.isDirectory
            })
            .map(_.getName).toSet

          val stored: ParSet[PluginID] = PLUGIN_STORE.keySet
          val toRemove: ParSet[PluginID] = if (stored.nonEmpty && modules.isEmpty) stored else stored.diff(modules)
          val toAdd: Set[PluginID] = modules.diff(stored)
          toRemove.foreach(x => taskRemoveEntry(x, true))
          toAdd.foreach(x => taskAddEntry(x, true))
          logger.info(s"Added: ${toAdd.mkString(",")} ")
          logger.info(s"Removed: ${toRemove.mkString(",")} ")
        }
      },
      20000,
      60000
    )
  }

  def buildState(xs: Iterable[Plugin]): PluginState = PluginState(
    xs.map {
      case x: PluginInstalled =>
        PluginProxy(x.id, Option(x.installedDate), true, x.active, x.registry.keys.toSeq)
      case PluginUninstalled(id) =>
        PluginProxy(id, None, false, false, Nil)
    }.toSeq
  )

  def taskStopWatch(): Unit = {
    TIMER.purge()
    TIMER.cancel()
  }
}
