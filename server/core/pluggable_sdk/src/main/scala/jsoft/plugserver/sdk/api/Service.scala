package jsoft.plugserver.sdk.api

import com.typesafe.scalalogging.LazyLogging

trait Service extends LazyLogging {

  def identifier: String

  def category: Category = ServiceCategory

  def description: String = "A service provider"

  def onStart(): Unit = {
    logger.info("onStart")
  }

  def onSuspend(): Unit = {
    logger.info("onSuspend")
  }

  def onActive(): Unit = {
    logger.info("onActive")
  }

  def onDestroy(): Unit = {
    logger.info("onDestroy")
  }
}
