package jsoft.plugserver.engine.core

import java.io.{File, FileFilter, FilenameFilter}
import java.net.URL
import java.util.{Timer, TimerTask}

import akka.http.scaladsl.model.{DateTime, StatusCodes}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import jsoft.plugserver.engine.model
import jsoft.plugserver.engine.model.Types.{PluginID, ServiceID}
import jsoft.plugserver.engine.model._
import jsoft.plugserver.engine.util.PluginImplicitsSupport
import jsoft.plugserver.sdk.{RestService, Service}

import scala.collection.parallel.mutable.ParMap
import scala.collection.parallel.{ParSet, mutable}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try}

trait PluginCore extends LazyLogging with PluginImplicitsSupport {

  val TIMER: Timer = new Timer()
  val PLUGIN_PATH: String = sys.props.getOrElse("pluginPath", "plugins")
  val POLLING_TIMEOUT: Int = sys.props.getOrElse("pollingTimeout", "3000").toInt
  val PLUGIN_STORE: mutable.ParMap[PluginID, Plugin] = ParMap.empty

  logger.info(s"Config: Reading plugins from: $PLUGIN_PATH")
  logger.info(s"Config: Polling timeout: $POLLING_TIMEOUT")

  def getActiveService(pluginID: PluginID, serviceID: ServiceID): Option[Service] = {
    PLUGIN_STORE.get(pluginID).collect { case x: PluginInstalled => x }.filter(_.active).flatMap(_.registry.get(serviceID)).filter(_.active).map(_.registry)
  }

  def getActiveRestService(pluginID: PluginID, serviceID: ServiceID): Option[RestService] = getActiveService(pluginID, serviceID).collect { case x: RestService => x }

  def getPlugin(pluginID: PluginID): Option[PluginInstalled] = PLUGIN_STORE.get(pluginID).collect { case x: PluginInstalled => x }

  def taskAvailablePlugin(pluginID: PluginID): Unit = {
    PLUGIN_STORE.put(pluginID, PluginUninstalled(pluginID))
  }

  def taskAddEntry(pluginID: String, installing: Boolean = false): Future[Unit] = Future {

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
      .foreach { urls =>

        val installed: PluginInstalled = PluginInstalled(pluginID, DateTime.now, urls, installing)
        PLUGIN_STORE.put(pluginID, installed)
        if (installing) {
          installed.registry.map { case (_, v) => v.registry.onStart() }
        }
      }
  }

  def taskRemoveEntry(pluginId: PluginID, delete: Boolean = false): Future[Unit] = Future {
    PLUGIN_STORE.get(pluginId).foreach { x =>

      if (delete) {
        PLUGIN_STORE -= pluginId
      } else {
        PLUGIN_STORE.put(pluginId, model.PluginUninstalled(pluginId))
      }

      x match {
        case plugin: PluginInstalled =>

          plugin.registry.values.foreach { x =>
            Future(x.registry.onDestroy()).onComplete {
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
      file.listFiles().filter(_.isDirectory).foreach(x => taskAddEntry(x.getName, installing = true))
    }
  }

  def taskWatchJars(): Unit = {

    TIMER.scheduleAtFixedRate(
      new TimerTask {
        override def run(): Unit = {

          val file: File = new File(PLUGIN_PATH)
          if (file.exists() && file.isDirectory) {

            val modules: Set[PluginID] = file
              .listFiles(new FileFilter {
                override def accept(pathname: File): Boolean = pathname.isDirectory
              })
              .map(_.getName).toSet

            val stored: ParSet[PluginID] = PLUGIN_STORE.keySet
            val toRemove: ParSet[PluginID] = if (stored.nonEmpty && modules.isEmpty) stored else stored.diff(modules)
            val toAdd: Set[PluginID] = modules.diff(stored)
            toRemove.foreach(x => taskRemoveEntry(x, delete = true))
            toAdd.foreach(x => taskAvailablePlugin(x))
          }
        }
      },
      POLLING_TIMEOUT,
      POLLING_TIMEOUT
    )
  }

  def pluginAsEnable(pluginID: PluginID): Future[Unit] = Future {
    getPlugin(pluginID).foreach { x =>
      PLUGIN_STORE.put(pluginID, x.asEnable)
      x.registry.foreach { case (_, v) => v.registry.onActive() }
    }
  }

  def pluginAsDisable(pluginID: PluginID): Future[Unit] = Future {
    getPlugin(pluginID).foreach { x =>
      PLUGIN_STORE.put(pluginID, x.asDisable)
      x.registry.foreach { case (_, v) => v.registry.onSuspend() }
    }
  }

  def reloadService(pluginID: PluginID, serviceID: ServiceID): Future[Unit] = Future {
    getActiveService(pluginID, serviceID).foreach { reg =>
      reg.onDestroy()
      reg.onStart()
    }
  }

  def taskServiceStatus(pluginID: PluginID, serviceID: ServiceID, action: String): Option[Route] = {
    getPlugin(pluginID)
      .map { plugin =>

        plugin.registry.get(serviceID) match {
          case Some(srv) =>

            action match {
              case "enable" => plugin.registry.put(serviceID, ServiceProxy(srv.registry))
              case "disable" => plugin.registry.put(serviceID, ServiceProxy(srv.registry, active = false))
              case _ => complete(StatusCodes.BadRequest, s"Action '$action' no suported.")
            }

            complete(StatusCodes.OK)

          case None => complete(StatusCodes.NotFound)
        }
      }
  }

  def redirectToService(pluginID: PluginID, serviceID: ServiceID): Option[Route] = {
    getActiveRestService(pluginID, serviceID).map(_.route)
  }

  def getPluginsState: PluginState = PluginState(
    PLUGIN_STORE.values.map {
      case x: PluginInstalled =>
        PluginInfo(
          x.id,
          Option(x.installedDate),
          installed = true,
          active = x.active,
          x.registry.map { case (k, v) => ServiceInfo(k, v.active) }.toList
        )
      case x: PluginUninstalled => PluginInfo(x.id, None, installed = false, active = false, Nil)
    }.toList
  )

  def taskStopWatch(): Unit = {
    TIMER.purge()
    TIMER.cancel()
  }
}
