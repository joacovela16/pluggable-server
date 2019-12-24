package jvc.prototype.common

import akka.http.scaladsl.server.Route

trait Registry {
  def identifier: String

  def route: Route
}
