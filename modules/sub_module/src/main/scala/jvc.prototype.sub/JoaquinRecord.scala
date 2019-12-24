package jvc.prototype.sub

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import jvc.prototype.common.Registry

class JoaquinRecord extends Registry with Directives {

  override def route: Route = get {
    complete(StatusCodes.OK, "BIENNNNNN!!!!")
  }

  override def identifier: String = "operation"
}
