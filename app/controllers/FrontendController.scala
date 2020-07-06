package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class FrontendController @Inject()(
	assets: Assets,
	cc: ControllerComponents
) extends AbstractController(cc) {

	def index: Action[AnyContent] = assets.at("index.html")
	def assetOrDefault(resource: String): Action[AnyContent] = assets.at(resource)
}
