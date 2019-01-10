/*
 * Copyright 2019 HM Revenue & Customs
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

import config.ApplicationConfig
import connectors.UserDetailsConnector
import helpers.helpers.I18nHelper
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import services.{SessionService, ShortLivedCache}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.play.frontend.config.AuthRedirects
import uk.gov.hmrc.play.frontend.controller.FrontendController

import scala.concurrent.Future

trait RasController extends FrontendController with I18nHelper with AuthorisedFunctions with AuthRedirects {

  val userDetailsConnector:UserDetailsConnector
  val sessionService: SessionService = SessionService
  val shortLivedCache: ShortLivedCache = ShortLivedCache

  def isAuthorised()(implicit request: Request[AnyContent]) = {
    authorised(AuthProviders(GovernmentGateway) and (Enrolment("HMRC-PSA-ORG") or Enrolment("HMRC-PP-ORG"))
    ).retrieve(authorisedEnrolments) {
      case (enrolments) =>
      Logger.info("User authorised")
      Future(Right(enrolments.enrolments.head.identifiers.head.value))
    } recover {
      case _ : NoActiveSession => Left(notLogged)
      case _ : AuthorisationException => Left(unAuthorise)
    }
  }

  def notLogged() = {Logger.warn("User not logged in - no active session found");Future.successful(toGGLogin(ApplicationConfig.loginCallback))}

  def unAuthorise() = {
    Logger.error("User not authorised");
    Future.successful(Redirect(routes.ErrorController.notAuthorised))
  }

  def userInfoNotFond(idName:String) = {
    Logger.error(s"${idName} not found");
    Future.successful(Redirect(routes.ErrorController.renderGlobalErrorPage()))
  }

}


