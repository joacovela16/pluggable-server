package jsoft.plugserver.sdk.api

import akka.http.scaladsl.server.Route

trait RestService extends Service {
  def route: Route

  override def category: Category = RestServiceCategory

  override def description: String = "A rest service provider"
}
