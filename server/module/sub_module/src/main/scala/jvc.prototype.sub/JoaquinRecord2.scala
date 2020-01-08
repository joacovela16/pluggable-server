package jvc.prototype.sub

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import jsoft.plugserver.sdk.RestService

class JoaquinRecord2 extends RestService with Directives {

  override def identifier: String = "core"

  override def route: Route = get {
    complete(StatusCodes.OK, "BIENNNNNN OTRA VEZZZZZZ!!!!")
  }
}
