package service

import config.WoofSource
import dao.WoofDAO
import model.Woof
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class WoofServiceTest extends PlaySpec with MockitoSugar with ScalaFutures {

	"A WoofService" should {
		"split woofs by configured pattern" in {
			val mockConfig = mock[Configuration]
			when(mockConfig get[Seq[WoofSource]] "woofs") thenReturn Seq(
				WoofSource(
					"test_woof_a",
					"Test Woof",
					raw"""(.*?):(.*?) time: (.*?) .*\s*""",
					Seq("Temperature", "Humidity"),
					"/dev/null"
				)
			)

			val woofDAO = mock[WoofDAO]
			when(woofDAO.insertWoof(any())) thenReturn Future.unit

			val woofService = new WoofService(mockConfig, woofDAO)
			val req = woofService.ingestWoof("test_woof_a", "64.76:123.4 time: 1523808307.7136409283 10.0.1.140 seq_no: 2793 -- Sun Apr 15 09:05:07 PDT 2018")
			await(req)
			verify(woofDAO, atLeastOnce()).insertWoof(Woof("test_woof_a_Temperature", 1523808307713L, 64.76))
			verify(woofDAO, atLeastOnce()).insertWoof(Woof("test_woof_a_Humidity", 1523808307713L, 123.4))
		}
	}
}
