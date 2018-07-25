/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import java.io.File

import connectors.UserDetailsConnector
import helpers.RandomNino
import helpers.helpers.I18nHelper
import models._
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import play.api.{Configuration, Environment, Mode}
import services.SessionService
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

class SessionControllerSpec extends UnitSpec with WithFakeApplication with I18nHelper with MockitoSugar {

  implicit val headerCarrier = HeaderCarrier()

  val mockConfig: Configuration = mock[Configuration]
  val mockEnvironment: Environment = Environment(mock[File], mock[ClassLoader], Mode.Test)
  val mockAuthConnector = mock[AuthConnector]
  val mockUserDetailsConnector = mock[UserDetailsConnector]
  val mockSessionService = mock[SessionService]

  val nino = MemberNino(RandomNino.generate)
  val dob = RasDate(Some("1"), Some("1"), Some("1999"))
  val memberDob = MemberDateOfBirth(dob)
  val residencyStatusResult = ResidencyStatusResult("",None,"","","","","")
  val rasSession = RasSession("",MemberName("Jim", "McGill"),nino, memberDob,residencyStatusResult,None)

  object TestSessionController extends SessionController{
    val authConnector: AuthConnector = mockAuthConnector
    override val userDetailsConnector: UserDetailsConnector = mockUserDetailsConnector
    override val config: Configuration = mockConfig
    override val env: Environment = mockEnvironment
    override val sessionService = mockSessionService
  }

  "SessionController" should {
    "redirect to target" when {

      "redirect is called with member-name in edit mode" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("member-name",false, true)(FakeRequest()))
        redirectLocation(result).get should include("member-name")
      }

      "redirect is called with member-nino in edit mode" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("member-nino",false, true)(FakeRequest()))
        redirectLocation(result).get should include("member-national-insurance-number")
      }

      "redirect is called with member-dob in edit mode" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("member-dob",false, true)(FakeRequest()))
        redirectLocation(result).get should include("member-date-of-birth")
      }

      "redirect is called with member-name and clean" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("member-name",true)(FakeRequest()))
        redirectLocation(result).get should include("member-name")
      }

      "redirect is called with member-nino and clean" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("member-nino",true)(FakeRequest()))
        redirectLocation(result).get should include("member-national-insurance-number")
      }

      "redirect is called with member-dob and clean" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("member-dob",true)(FakeRequest()))
        redirectLocation(result).get should include("member-date-of-birth")
      }

      "redirect is called with member-name" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("member-name",false)(FakeRequest()))
        redirectLocation(result).get should include("member-name")
      }

      "redirect is called with member-nino" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("member-nino",false)(FakeRequest()))
        redirectLocation(result).get should include("member-national-insurance-number")
      }

      "redirect is called with member-dob" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("member-dob",false)(FakeRequest()))
        redirectLocation(result).get should include("member-date-of-birth")
      }

      "redirect is called with start" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("start",false)(FakeRequest()))
        redirectLocation(result).get should include("relief-at-source")
      }

      "redirect is called with chooseAnOption" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("chooseAnOption",false)(FakeRequest()))
        redirectLocation(result).get should include("relief-at-source")
      }

      "redirect is called with chooseAnOption and clean" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("chooseAnOption",true)(FakeRequest()))
        redirectLocation(result).get should include("relief-at-source")
      }
    }

    "redirect to global error page" when {
      "no ras session is returned (target is irrelevant here)" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(None))
        val result = await(TestSessionController.redirect("member-details", false)(FakeRequest()))
        redirectLocation(result).get should include("global-error")
      }

      "no ras session is returned (target is irrelevant here) and clean is true" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(None))
        val result = await(TestSessionController.redirect("member-details", true)(FakeRequest()))
        redirectLocation(result).get should include("global-error")
      }

      "ras session is returned but target is not recognised" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("blah blah", false)(FakeRequest()))
        redirectLocation(result).get should include("global-error")
      }

      "ras session is returned but target is not recognised and clean is true" in {
        when(mockSessionService.resetRasSession()(Matchers.any())).thenReturn(Future.successful(Some(rasSession)))
        val result = await(TestSessionController.redirect("blah blah", true)(FakeRequest()))
        redirectLocation(result).get should include("global-error")
      }
    }

    "keep a session alive" when {
      "keep alive action is called" in {
        val result = await(TestSessionController.keepAlive()(FakeRequest()))
        status(result) shouldBe OK
      }
    }
  }



}
