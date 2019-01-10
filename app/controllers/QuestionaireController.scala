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

import config.{RasContext, RasContextImpl}
import play.api.mvc.Action
import uk.gov.hmrc.play.frontend.controller.FrontendController
import models.Questionnaire
import play.api.i18n.Messages.Implicits._
import play.api.Play.current
import services.AuditService

import scala.concurrent.Future


trait QuestionnaireController extends FrontendController {

  implicit val context: RasContext = RasContextImpl
  val auditService: AuditService

  def showQuestionnaire = Action.async { implicit request =>
    Future.successful(Ok(views.html.feedback.feedbackQuestionaire(Questionnaire.form)))
  }

  def submitQuestionnaire = Action.async { implicit request =>
      Questionnaire.form.bindFromRequest.fold(
        formWithErrors => {
          Future.successful(BadRequest(views.html.feedback.feedbackQuestionaire(formWithErrors))
          )
        },
        data => {
          auditService.audit(auditType="rasFeedbackSurvey", path=routes.QuestionnaireController.submitQuestionnaire().url,CreateQuestionnaireAudit(data))
          Future.successful(Redirect(routes.QuestionnaireController.feedbackThankyou()))
        }
      )
  }

  def feedbackThankyou = Action.async { implicit request =>
      Future.successful(Ok(views.html.feedback.thanks()))
  }

  private def CreateQuestionnaireAudit(survey: Questionnaire): Map[String, String] = {
    Map(
      "easyToUse" -> survey.easyToUse.mkString,
      "satisfactionLevel" -> survey.satisfactionLevel.mkString,
      "whyGiveThisRating" -> survey.whyGiveThisRating.mkString
    )
  }
}

object QuestionnaireController extends QuestionnaireController {
  override val auditService: AuditService = AuditService
}