import javax.inject.Inject
import model.User
import play.api.mvc._
import service.LoginService

import scala.concurrent.{ExecutionContext, Future}

package object controllers {
  case class UserRequest[A](user: User, token: String, request: Request[A]) extends WrappedRequest[A](request)

  class AuthActions @Inject()(loginService: LoginService, parser: BodyParsers.Default)(implicit val ec: ExecutionContext) {

    sealed trait AuthBuilder {
      def parser: BodyParser[AnyContent] = AuthActions.this.parser
      def executionContext: ExecutionContext = AuthActions.this.ec
    }

    object user extends ActionBuilder[UserRequest, AnyContent] with ActionRefiner[Request, UserRequest] with AuthBuilder {
      override protected[controllers] def refine[A](request: Request[A]): Future[Either[Result, UserRequest[A]]] = {
        val token = request.session.get("token").getOrElse("")
        Future.successful(loginService.getSession(token) match {
          case Some(session) => Right(UserRequest(User(session.username, session.isAdmin), token, request))
          case _ => Left(Results.Unauthorized.withNewSession)
        })
      }
    }

    object admin extends ActionBuilder[UserRequest, AnyContent] with ActionRefiner[Request, UserRequest] with AuthBuilder {
      override protected[controllers] def refine[A](request: Request[A]): Future[Either[Result, UserRequest[A]]] = user.refine(request).map(_.flatMap(ur => {
        if (ur.user.isAdmin) {
          Right(ur)
        } else {
          Left(Results.Unauthorized.withNewSession)
        }
      }))
    }
  }
}
