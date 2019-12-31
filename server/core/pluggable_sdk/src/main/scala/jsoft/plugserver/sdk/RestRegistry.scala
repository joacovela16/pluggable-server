package jsoft.plugserver.sdk

import akka.http.scaladsl.server.Route

trait RestRegistry extends Registry {
  def route: Route
}
