package com.dancingrobot84.sbt.remote

import com.intellij.openapi.diagnostic

import scala.language.implicitConversions

/**
 * @author: Nikolay Obedin
 * @since: 2/18/15.
 */
trait Logger {
  def log(message: String, level: Logger.Level, cause: Option[Throwable]): Unit

  def debug(message: String, cause: Option[Throwable] = None): Unit =
    log(message, Logger.Level.Debug, cause)
  def info(message: String, cause: Option[Throwable] = None): Unit =
    log(message, Logger.Level.Info, cause)
  def warn(message: String, cause: Option[Throwable] = None): Unit =
    log(message, Logger.Level.Warn, cause)
  def error(message: String, cause: Option[Throwable] = None): Unit =
    log(message, Logger.Level.Error, cause)

  def debug(message: String, cause: Throwable): Unit =
    debug(message, Option(cause))
  def info(message: String, cause: Throwable): Unit =
    info(message, Option(cause))
  def warn(message: String, cause: Throwable): Unit =
    warn(message, Option(cause))
  def error(message: String, cause: Throwable): Unit =
    error(message, Option(cause))
}

object Logger {
  sealed trait Level

  object Level {
    case object Debug extends Level
    case object Info extends Level
    case object Warn extends Level
    case object Error extends Level

    def fromString(level: String): Level = level.toLowerCase match {
      case "debug" => Level.Debug
      case "warn"  => Level.Warn
      case "error" => Level.Error
      case _       => Level.Info
    }
  }

  implicit def ideaLogger2Logger(logger: diagnostic.Logger): Logger = new Logger {
    override def log(message: String, level: Logger.Level, cause: Option[Throwable]): Unit = level match {
      case Level.Info  => logger.info(message, cause.orNull)
      case Level.Warn  => logger.warn(message, cause.orNull)
      case Level.Error => logger.error(message, cause.orNull)
      case _           => logger.debug(message, cause.orNull)
    }
  }
}
