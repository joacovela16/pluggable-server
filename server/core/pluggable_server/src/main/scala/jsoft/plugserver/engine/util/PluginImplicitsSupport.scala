package jsoft.plugserver.engine.util

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Failure

trait PluginImplicitsSupport extends Directives with LazyLogging {

  implicit def futureComplete(f: Future[Unit]): Route = {
    onComplete(f) {
      case Failure(exception) =>
        logger.error(exception.getLocalizedMessage, exception)
        complete(StatusCodes.InternalServerError)
      case _ =>
        complete(StatusCodes.OK)
    }
  }

  implicit def optionResponse[T](x: Option[Route]): Route = x.getOrElse(complete(StatusCodes.NotFound))
}
