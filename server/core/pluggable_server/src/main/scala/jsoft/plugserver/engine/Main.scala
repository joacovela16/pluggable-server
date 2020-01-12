package jsoft.plugserver.engine

import java.io.File

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{HttpApp, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import jsoft.plugserver.engine.core.PluginCore
import jsoft.plugserver.engine.util.PluginImplicitsSupport
import net.lingala.zip4j.ZipFile

import scala.util.Failure

object Main extends HttpApp with FailFastCirceSupport with PluginImplicitsSupport with PluginCore {

  override protected def routes: Route ={
    concat(
      (get & pathPrefix("app")) {
        pathEndOrSingleSlash {
          get(getFromResource("app/index.html"))
        } ~
          getFromResourceDirectory("app")
      },
      path("state") {
        get(complete(StatusCodes.OK, getPluginsState))
      },
      path("config" / "plugin" / Segment / Segment / "reload") { (pluginID, serviceID) =>
        get(reloadService(pluginID, serviceID))
      },
      path("config" / "plugin" / Segment / Segment) { (pluginId, action) =>
        get {
          action match {
            case "enable" => pluginAsEnable(pluginId)
            case "disable" => pluginAsDisable(pluginId)
            case "install" => taskAddEntry(pluginId, installing = true)
            case "uninstall" => taskRemoveEntry(pluginId)
            case "destroy" => taskRemoveEntry(pluginId, delete = true)
            case _ => complete(StatusCodes.BadRequest, "Undefined operation")
          }
        }
      },
      path("upload") {
        withoutSizeLimit {
          import scala.concurrent.ExecutionContext.Implicits.global
          storeUploadedFile("file", fileInfo => new File(s"$PLUGIN_PATH/${fileInfo.fileName}")) {
            case (fileInfo, file) =>

              val module: String = fileInfo.fileName.substring(0, fileInfo.fileName.lastIndexOf("."))

              onComplete {
                taskRemoveEntry(module, delete = true)
                  .map { _ =>
                    new ZipFile(file.getAbsolutePath).extractAll(s"$PLUGIN_PATH/")
                    file.delete()
                  }
              } {
                case Failure(exception) =>
                  logger.error(exception.getLocalizedMessage, exception)
                  complete(StatusCodes.InternalServerError)
                case _ => complete(StatusCodes.OK)
              }
          }
        }
      },
      path("config" / "service" / Segment / Segment / Segment) { (pluginID, serviceID, action) => taskServiceStatus(pluginID, serviceID, action) },
      path("service" / Segment / Segment) { (pluginID, registryID) =>
        redirectToService(pluginID, registryID).getOrElse(complete(StatusCodes.NotFound))
      }
    )
  }

  def main(args: Array[String]): Unit = {
    taskLoadCreated()
    taskWatchJars()
    startServer("localhost", 8080)
    taskStopWatch()
  }
}
