package jvc.serverplug.engine

import java.io.File
import java.net.URLClassLoader
import java.nio.file._
import java.util.ServiceLoader

import akka.http.scaladsl.model.{DateTime, StatusCodes}
import akka.http.scaladsl.server.{HttpApp, Route, StandardRoute}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import jvc.prototype.pluggable.sdk.{Registry, RestRegistry}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Main extends HttpApp with LazyLogging with FailFastCirceSupport {

  private val PLUGIN_PATH: String = sys.props.getOrElse("pluginPath", "plugins")
  private lazy val watchService: WatchService = FileSystems.getDefault.newWatchService()
  private val pluginStore: mutable.Map[PluginID, Plugin] = mutable.Map.empty

  def taskSendReport: StandardRoute = complete(StatusCodes.OK)

  override protected def routes: Route = {
    path("state") {
      get(complete(StatusCodes.OK, buildState(pluginStore.values)))
    } ~
      path("plugin" / Segment / Segment / "reload") { (pluginID, serviceID) =>
        get {
          pluginStore.get(pluginID).collect { case x: PluginInstalled => x }.filter(_.active) match {
            case Some(plugin) => plugin.registry.get(serviceID) match {
              case Some(value) =>
                onComplete {
                  Future {
                    value.onDestroy()
                    value.onStart()
                  }
                } {
                  case Failure(exception) =>
                    logger.error(exception.getLocalizedMessage, exception)
                    complete(StatusCodes.InternalServerError)
                  case _ => complete(StatusCodes.OK)
                }
              case None => complete(StatusCodes.NotImplemented, s"Undefined service {$serviceID}")
            }
            case None => complete(StatusCodes.NotImplemented, s"Plugin {$pluginID} unknown or disabled")
          }
        }
      } ~
      path("plugin" / Segment / Segment) { (pluginId, action) =>
        get {

          action match {
            case "enable" =>

              pluginStore.get(pluginId).collect { case x: PluginInstalled => x } match {
                case Some(plugin) =>

                  pluginStore.put(pluginId, PluginInstalled(pluginId, plugin.installedDate, plugin.classLoader, plugin.registry, true))
                  Future(plugin.registry.values.foreach(_.onActive()))
                  taskSendReport

                case None => complete(StatusCodes.NotFound, s"Plugin $pluginId not founded")
              }

            case "disable" =>

              pluginStore.get(pluginId).collect { case x: PluginInstalled => x } match {
                case Some(plugin) =>

                  pluginStore.put(pluginId, PluginInstalled(pluginId, plugin.installedDate, plugin.classLoader, plugin.registry, false))
                  Future(plugin.registry.values.foreach(_.onSuspend()))
                  taskSendReport

                case None =>
                  complete(StatusCodes.NotFound, s"Plugin $pluginId not founded")
              }

            case "install" =>
              taskAddEntry(s"$pluginId.jar", true)
              taskSendReport
            case "uninstall" =>

              taskRemoveEntry(pluginId)

              taskSendReport

            case _ => complete(StatusCodes.BadRequest, "Undefined operation")
          }
        }
      } ~
      path("service" / Segment / Segment) { (pluginID, serviceID) =>
        pluginStore.get(pluginID).collect { case x: PluginInstalled => x }.filter(_.active) match {
          case Some(plugin) => plugin.registry.get(serviceID) match {
            case Some(value) =>
              value match {
                case registry: RestRegistry => registry.route
                case _ => complete(StatusCodes.BadRequest)
              }
            case None => complete(StatusCodes.NotImplemented, s"Undefined service {$serviceID}")
          }
          case None => complete(StatusCodes.NotImplemented, s"Plugin {$pluginID} unknown or disabled")
        }
      }
  }

  def taskAddEntry(filename: String, forceActive: Boolean = false): Unit = {

    Option(filename)
      .filter(_.endsWith(".jar"))
      .map(x => new File(s"$PLUGIN_PATH/$x"))
      .map(_.toURI.toURL)
      .flatMap(url => Try(URLClassLoader.newInstance(Array(url))).toOption)
      .flatMap { ucl => Try(ServiceLoader.load(classOf[Registry], ucl)).toOption.map(x => (ucl, x)) }
      .foreach { case (ucl, srv) =>

        val lastIndexOf: Int = filename.lastIndexOf(".")
        val pluginID: PluginID = filename.substring(0, lastIndexOf)

        val tmpMap: mutable.Map[RegistryID, Registry] = mutable.Map.empty
        srv.forEach { x =>
          logger.info(x.getClass.getSimpleName)
          tmpMap.put(x.identifier, x)
        }

        logger.info(s"Plugin $pluginID installed. Included ${tmpMap.size} services")

        pluginStore.put(pluginID, PluginInstalled(pluginID, DateTime.now, ucl, tmpMap.toMap, forceActive))

        if (forceActive) {
          tmpMap.values.foreach { r => Future(r.onStart()) }
        }
      }
  }

  def taskRemoveEntry(pluginId: PluginID): Unit = {
    pluginStore.get(pluginId).collect { case x: PluginInstalled => x }.foreach { plugin =>

      Try(plugin.classLoader.close()) match {
        case Failure(exception) => logger.error(exception.getLocalizedMessage, exception)
        case _ =>

          pluginStore.put(pluginId, PluginUninstalled(pluginId))

          plugin.registry.values.foreach { x =>
            Future(x.onDestroy()).onComplete {
              case Failure(exception) => logger.error(exception.getLocalizedMessage, exception)
              case _ =>
            }
          }
      }
    }
  }

  def taskLoadCreated(): Unit = {
    val file: File = new File(PLUGIN_PATH)

    if (file.exists()) {
      file
        .list { case (_, name: String) => name.endsWith(".jar") }
        .foreach(x => taskAddEntry(x, forceActive = true))
    }
  }

  def taskWatchJars(): Unit = {
    new Thread {
      override def run() {

        Paths.get(PLUGIN_PATH).register(watchService,
          StandardWatchEventKinds.ENTRY_MODIFY
        )

        Try {
          while (true) {

            val key: WatchKey = watchService.take()
            key.pollEvents().forEach { x =>

              val context: String = x.context().toString

              x.kind().toString match {
                case "ENTRY_MODIFY" => taskAddEntry(context)
              }
            }
          }
        }
      }
    }.start()
  }

  def taskRunServer(): Unit = startServer("localhost", 8080)

  def taskStopWatch(): Unit = watchService.close()

  def main(args: Array[String]): Unit = {
    taskLoadCreated()
    taskWatchJars()
    taskRunServer()
    taskStopWatch()
  }
}
