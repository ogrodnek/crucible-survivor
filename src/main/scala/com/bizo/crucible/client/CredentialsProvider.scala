package com.bizo.crucible.client

trait CredentialsProvider {
  def user(): String  
  def password(): String
}

class BasicCredentialsProvider(val user: String, val password: String) extends CredentialsProvider
class EnvironmentCredentialsProvider extends BasicCredentialsProvider(Env("CRUCIBLE_USER"), Env("CRUCIBLE_PASS"))