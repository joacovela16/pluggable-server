package jsoft.plugserver.sdk

import com.typesafe.scalalogging.LazyLogging

trait Registry extends LazyLogging {

  def identifier: String

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
