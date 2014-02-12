package com.bizo.crucible.client

object Env {
  /** Returns the system property or environment variable with given name, or default if undefined. */
  def apply(name: String, default: String = null): String = {
    Option(System.getProperty(name, System.getenv(name))) getOrElse {
      Option(default) getOrElse { throw new MissingRequiredProperty(name) }
    }
  }

  class MissingRequiredProperty(name: String) extends RuntimeException(s"Missing Required Property: $name")
}