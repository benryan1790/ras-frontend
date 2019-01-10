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

import config.{FrontendAuthConnector, RasContext, RasContextImpl}
import connectors.UserDetailsConnector
import helpers.helpers.I18nHelper
import play.api.{Configuration, Environment, Logger, Play}
import play.api.mvc.Action
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.Future

object ErrorController extends ErrorController {
  val authConnector: AuthConnector = FrontendAuthConnector
  override val userDetailsConnector: UserDetailsConnector = UserDetailsConnector
  val config: Configuration = Play.current.configuration
  val env: Environment = Environment(Play.current.path, Play.current.classloader, Play.current.mode)
}

trait ErrorController extends RasController with I18nHelper {

  implicit val context: RasContext = RasContextImpl

  def renderGlobalErrorPage = Action.async {
    implicit request =>
      isAuthorised.flatMap {
        case Right(_) =>
          Logger.info("[ErrorController][renderGlobalErrorPage] rendering global error page")
          Future.successful(InternalServerError(views.html.global_error()))
        case Left(resp) =>
          Logger.error("[ErrorController][renderGlobalErrorPage] user not authorised")
          resp
      }
  }

  def renderProblemUploadingFilePage = Action.async {
    implicit request =>
      isAuthorised.flatMap {
        case Right(_) =>
          Logger.info("[ErrorController][renderProblemUploadingFilePage] rendering problem uploading file page")
          Future.successful(InternalServerError(views.html.problem_uploading_file()))
        case Left(resp) =>
          Logger.error("[ErrorController][renderProblemUploadingFilePage] user not authorised")
          resp
      }
  }

  def fileNotAvailable = Action.async {
    implicit request =>
      isAuthorised.flatMap {
        case Right(_) =>
          Logger.info("[ErrorController][fileNotAvailable] rendering file not available page")
          Future.successful(InternalServerError(views.html.file_not_available()))
        case Left(resp) =>
          Logger.error("[ErrorController][fileNotAvailable] user not authorised")
          resp
      }
  }

  def notAuthorised = Action.async {
    implicit request =>
      Future.successful(Ok(views.html.unauthorised()))
  }
}
