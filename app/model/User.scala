package model

case class LoginRequest(username: String, password: String)
case class LoginResponse(username: String, isAdmin: Boolean, token: String)

case class User(username: String, isAdmin: Boolean)
