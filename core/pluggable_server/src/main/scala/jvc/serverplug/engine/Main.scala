package jvc.serverplug.engine

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{HttpApp, Route}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import jvc.serverplug.engine.util.PluginImplicitsSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Failure

object Main extends HttpApp with LazyLogging with FailFastCirceSupport with PluginImplicitsSupport {

  override protected def routes: Route = {
    path("state") {
      get(complete(StatusCodes.OK, buildState(PLUGIN_STORE.values)))
    } ~
      path("plugin" / Segment / Segment / "reload") { (pluginID, serviceID) =>
        get {

          getActiveService(pluginID, serviceID) match {
            case Some(service) =>
              Future {
                service.onDestroy()
                service.onStart()
              }.onComplete {
                case Failure(exception) => logger.error(exception.getLocalizedMessage, exception)
                case _ =>
              }

              complete(StatusCodes.OK)

            case None => complete(StatusCodes.NotFound, s"Plugin {$pluginID} unknown or disabled")
          }
        }
      } ~
      path("plugin" / Segment / Segment) { (pluginId, action) =>
        get {

          action match {
            case "enable" =>

              getPlugin(pluginId) match {
                case Some(plugin) =>

                  PLUGIN_STORE.put(pluginId, plugin.asEnable)
                  Future(plugin.registry.values.foreach(_.onActive()))
                  complete(StatusCodes.OK)

                case None => complete(StatusCodes.NotFound, s"Plugin $pluginId not founded")
              }

            case "disable" =>

              getPlugin(pluginId) match {
                case Some(plugin) =>
                  PLUGIN_STORE.put(pluginId, plugin.asDisable)
                  Future(plugin.registry.values.foreach(_.onSuspend()))
                  complete(StatusCodes.OK)

                case None => complete(StatusCodes.NotFound, s"Plugin $pluginId not founded")
              }

            case "install" =>
              taskAddEntry(pluginId, true)
              complete(StatusCodes.OK)

            case "uninstall" =>

              taskRemoveEntry(pluginId)
              complete(StatusCodes.OK)

            case _ => complete(StatusCodes.BadRequest, "Undefined operation")
          }
        }
      } ~
      path("service" / Segment / Segment) { (pluginID, serviceID) =>
        getActiveRestService(pluginID, serviceID) match {
          case Some(service) => service.route
          case None => complete(StatusCodes.BadRequest)
        }
      }
  }

  def main(args: Array[String]): Unit = {
    taskLoadCreated()
    taskWatchJars()
    startServer("localhost", 8080)
    taskStopWatch()
  }
}
