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

import config.FrontendAuthConnector
import connectors.UserDetailsConnector
import play.api.{Configuration, Environment, Play}
import play.api.mvc.Result
import uk.gov.hmrc.auth.core.AuthConnector

object PageFlowController extends PageFlowController {
  // $COVERAGE-OFF$Disabling highlighting by default until a workaround for https://issues.scala-lang.org/browse/SI-8596 is found
  val authConnector: AuthConnector = FrontendAuthConnector
  override val userDetailsConnector: UserDetailsConnector = UserDetailsConnector
  val config: Configuration = Play.current.configuration
  val env: Environment = Environment(Play.current.path, Play.current.classloader, Play.current.mode)
  // $COVERAGE-ON$
}

trait PageFlowController extends RasController {

  val FILE_UPLOAD = "FileUploadController"
  val MEMBER_NAME = "MemberNameController"
  val MEMBER_NINO = "MemberNinoController"
  val MEMBER_DOB = "MemberDOBController"
  val RESULTS = "ResultsController"

  def previousPage(from: String, edit: Boolean = false): Result = {
    from match {
      case FILE_UPLOAD => Redirect(routes.ChooseAnOptionController.get)
      case MEMBER_NAME => {
        edit match {
          case true => Redirect(routes.ResultsController.noMatchFound)
          case _ => Redirect(routes.ChooseAnOptionController.get)
        }
      }
      case MEMBER_NINO => {
        edit match {
          case true => Redirect(routes.ResultsController.noMatchFound)
          case _ => Redirect(routes.MemberNameController.get())
        }
      }
      case MEMBER_DOB => {
        edit match {
          case true => Redirect(routes.ResultsController.noMatchFound)
          case _ => Redirect(routes.MemberNinoController.get())
        }
      }
      case RESULTS => Redirect(routes.MemberDOBController.get())
      case _ => Redirect(routes.ErrorController.renderGlobalErrorPage())
    }
  }
}

