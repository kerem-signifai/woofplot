package bootstrap

import java.security.SecureRandom

import play.core.server.ProdServerStart

object WoofPlot {
  def main(args: Array[String]): Unit = {
    val csrng = new SecureRandom()
    def nextChar: Char = (csrng.nextInt(74) + 48).toChar
    val secret = (1 to 64).map(_ => nextChar).mkString.replaceAll("\\\\+", "/")

    System.setProperty("play.http.secret.key", secret)
    ProdServerStart.main(args)
  }
}
