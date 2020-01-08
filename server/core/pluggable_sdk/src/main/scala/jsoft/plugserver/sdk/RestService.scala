package jsoft.plugserver.sdk

import akka.http.scaladsl.server.Route

trait RestService extends Service {
  def route: Route
}
