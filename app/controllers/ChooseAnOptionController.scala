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

import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import config.{FrontendAuthConnector, RasContext, RasContextImpl}
import connectors.{ResidencyStatusAPIConnector, UserDetailsConnector}
import org.joda.time.DateTime
import play.api.http.HttpEntity
import play.api.mvc._
import play.api.{Configuration, Environment, Logger, Play}
import uk.gov.hmrc.auth.core.AuthConnector
import helpers.helpers.I18nHelper
import models.{FileSession, FileUploadStatus}
import services.ShortLivedCache
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.time.TaxYearResolver
import models.FileUploadStatus._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ChooseAnOptionController extends ChooseAnOptionController {
  // $COVERAGE-OFF$Disabling highlighting by default until a workaround for https://issues.scala-lang.org/browse/SI-8596 is found
  val authConnector: AuthConnector = FrontendAuthConnector
  override val userDetailsConnector: UserDetailsConnector = UserDetailsConnector
  val config: Configuration = Play.current.configuration
  val env: Environment = Environment(Play.current.path, Play.current.classloader, Play.current.mode)
  override val resultsFileConnector:ResidencyStatusAPIConnector = ResidencyStatusAPIConnector
  // $COVERAGE-ON$
}

trait ChooseAnOptionController extends RasController with PageFlowController with I18nHelper {

  val resultsFileConnector: ResidencyStatusAPIConnector
  implicit val context: RasContext = RasContextImpl
  private val _contentType =   "application/csv"

  def get = Action.async {
    implicit request =>
      isAuthorised.flatMap {
        case Right(userId) =>
          shortLivedCache.fetchFileSession(userId).flatMap { fileSession =>
            shortLivedCache.determineFileStatus(userId).flatMap {
              fileStatus =>
                Logger.info(s"[ChooseAnOptionController][get] determine file status returned $fileStatus")
                Future.successful(Ok(views.html.choose_an_option(fileStatus, getHelpDate(fileStatus, fileSession))))
            }
          }
        case Left(resp) =>
          Logger.error("[ChooseAnOptionController][get] user not authorised")
          resp
      }
  }

  private def getHelpDate(fileStatus: FileUploadStatus.Value, fileSession: Option[FileSession]): Option[String] = {
    fileSession match {
      case Some(fileSession) =>
        fileStatus match {
          case Ready => Some(formattedExpiryDate(fileSession.resultsFile.get.uploadDate.get)) // will always have a resultsfile and date if ready
          case InProgress => Some(formattedUploadDate(fileSession.uploadTimeStamp.get)) // will always have an upload date time if in progress
          case _ => None
        }
      case _ => None
    }
  }

  private def formattedExpiryDate(timestamp: Long): String = {
    val expiryDate = new DateTime(timestamp).plusDays(3)
    s"${expiryDate.toString("EEEE d MMMM yyyy")} at ${expiryDate.toString("H:mma").toLowerCase()}"
  }

  private def formattedUploadDate(timestamp: Long): String = {
    val uploadDate = new DateTime(timestamp)

    val todayOrYesterday = uploadDate.toLocalDate().isEqual(DateTime.now.toLocalDate) match {
      case true => Messages("today")
      case _ => Messages("yesterday")
    }
    Messages("formatted.upload.timestamp", todayOrYesterday, uploadDate.toString("H:mm"))
  }

  def renderUploadResultsPage = Action.async {
    implicit request =>
      isAuthorised.flatMap {
        case Right(userId) =>
          sessionService.hasUserDimissedUrBanner().flatMap { urBannerDismissed =>
            shortLivedCache.fetchFileSession(userId).map {
              case Some(fileSession) =>
                fileSession.resultsFile match {
                  case Some(resultFile) =>
                    resultFile.uploadDate match {
                      case Some(timestamp) =>
                        fileSession.userFile match {
                          case Some(callbackData) =>
                            val currentTaxYear = TaxYearResolver.currentTaxYear
                            val filename = ShortLivedCache.getDownloadFileName(fileSession)
                            Ok(views.html.upload_result(callbackData.fileId, formattedExpiryDate(timestamp), isBeforeApr6(timestamp), currentTaxYear, filename, !urBannerDismissed))
                          case _ =>
                            Logger.error("[ChooseAnOptionController][renderUploadResultsPage] failed to retrieve callback data")
                            Redirect(routes.ErrorController.renderGlobalErrorPage)
                        }
                      case _ =>
                        Logger.error("[ChooseAnOptionController][renderUploadResultsPage] failed to retrieve upload timestamp")
                        Redirect(routes.ErrorController.renderGlobalErrorPage)
                    }
                  case _ =>
                    Logger.info("[ChooseAnOptionController][renderUploadResultsPage] file upload in progress")
                    Redirect(routes.ChooseAnOptionController.renderNoResultsAvailableYetPage)
                }
              case _ =>
                Logger.info("[ChooseAnOptionController][renderUploadResultsPage] no results available")
                Redirect(routes.ChooseAnOptionController.renderNoResultAvailablePage)
            }
          }
        case Left(resp) =>
          Logger.error("[ChooseAnOptionController][renderUploadResultsPage] user not authorised")
          resp
      }
  }

  def renderNoResultAvailablePage = Action.async {
    implicit request =>
      isAuthorised.flatMap {
        case Right(userId) =>
          shortLivedCache.fetchFileSession(userId).flatMap {
            case Some(fileSession) =>
              fileSession.resultsFile match {
                case Some(resultFile) =>
                  Logger.info("[ChooseAnOptionController][renderNotResultAvailablePage] there is a file ready, rendering results page")
                  Future.successful(Redirect(routes.ChooseAnOptionController.renderUploadResultsPage()))
                case _ =>
                  Logger.info("[ChooseAnOptionController][renderNotResultAvailablePage] there is a file in progress, rendering results-not-available page")
                  Future.successful(Redirect(routes.ChooseAnOptionController.renderNoResultsAvailableYetPage()))
              }
            case _ =>
              Logger.info("[ChooseAnOptionController][renderNotResultAvailablePage] rendering no result available page")
              Future.successful(Ok(views.html.no_results_available()))
          }
        case Left(resp) =>
          Logger.error("[ChooseAnOptionController][renderNotResultAvailablePage] user not authorised")
          resp
      }
  }

  def renderNoResultsAvailableYetPage = Action.async {
    implicit request =>
      isAuthorised.flatMap {
        case Right(userId) =>
          shortLivedCache.fetchFileSession(userId).flatMap {
            case Some(fileSession) =>
              fileSession.resultsFile match {
                case Some(_) =>
                  Logger.info("[ChooseAnOptionController][renderNotResultAvailableYetPage] redirecting to results page")
                  Future.successful(Redirect(routes.ChooseAnOptionController.renderUploadResultsPage()))
                case _ =>
                  Logger.info("[ChooseAnOptionController][renderNotResultAvailableYetPage] rendering results not available page")
                  Future.successful(Ok(views.html.results_not_available_yet()))
              }
            case _ =>
              Logger.info("[ChooseAnOptionController][renderNotResultAvailableYetPage] redirecting to results not available")
              Future.successful(Redirect(routes.ChooseAnOptionController.renderNoResultAvailablePage()))
          }
        case Left(resp) =>
          Logger.error("[ChooseAnOptionController][renderNotResultAvailableYetPage] user not authorised")
          resp
      }
  }

  def renderFileReadyPage = Action.async {
    implicit request =>
      isAuthorised.flatMap {
        case Right(userId) =>
          shortLivedCache.fetchFileSession(userId).flatMap {
            case Some(fileSession) =>
              fileSession.resultsFile match {
                case Some(resultFile) =>
                  Future.successful(Ok(views.html.file_ready()))
                case _ =>
                  Logger.error("[ChooseAnOptionController][renderFileReadyPage] session has no result file")
                  Future.successful(Redirect(routes.FileUploadController.uploadInProgress()))
              }
            case _ =>
              Logger.error("[ChooseAnOptionController][renderFileReadyPage] failed to retrieve session")
              Future.successful(Redirect(routes.ErrorController.renderGlobalErrorPage))
          }
        case Left(resp) =>
          Logger.error("[ChooseAnOptionController][renderFileReadyPage] user not authorised")
          resp
      }
  }

  def getResultsFile(fileName:String) = Action.async {
    implicit request =>
      isAuthorised.flatMap {
        case Right(userId) =>
          shortLivedCache.fetchFileSession(userId).flatMap {
            case Some(fileSession) =>
              fileSession.resultsFile match {
                case Some(resultFile) =>
                  resultFile.filename match {
                    case Some(name) if name == fileName =>
                      shortLivedCache.removeFileSessionFromCache(userId)
                      getFile(fileName, userId, shortLivedCache.getDownloadFileName(fileSession))
                    case _ =>
                      Logger.error("[ChooseAnOptionController][getResultsFile] filename empty")
                      Future.successful(Redirect(routes.ErrorController.fileNotAvailable))
                  }
                case _ =>
                  Logger.error("[ChooseAnOptionController][getResultsFile] no result file found")
                  Future.successful(Redirect(routes.ErrorController.fileNotAvailable))
              }
            case _ =>
              Logger.error("[ChooseAnOptionController][getResultsFile] no file session for User Id")
              Future.successful(Redirect(routes.ErrorController.fileNotAvailable))
          }
        case Left(resp) =>
          Logger.error("[ChooseAnOptionController][getResultsFile] user not authorised")
          resp
      }
  }

  private def getFile(fileName: String, userId: String, downloadFileName: String)(implicit hc: HeaderCarrier): Future[play.api.mvc.Result] = {
    resultsFileConnector.getFile(fileName, userId).map { response =>
      val dataContent: Source[ByteString, _] = StreamConverters.fromInputStream(() => response.get)
        .watchTermination()((_, futDone) => futDone.onComplete {
          case Success(_) =>
            Logger.info(s"File with name $fileName has started to delete")
            resultsFileConnector.deleteFile(fileName, userId)
          case Failure(t) =>
            Logger.warn(s"File with name $fileName was not deleted")
        })

      Ok.sendEntity(HttpEntity.Streamed(dataContent, None, Some(_contentType)))
        .withHeaders(CONTENT_DISPOSITION -> s"""attachment; filename="${downloadFileName}-results.csv"""",
          // CONTENT_LENGTH -> s"${fileData.get.length}",
          CONTENT_TYPE -> _contentType)

    }.recover {
      case ex: Throwable => Logger.error("Request failed with Exception " + ex.getMessage + " for file -> " + fileName)
        Redirect(routes.ErrorController.renderGlobalErrorPage)
    }
  }

  private def isBeforeApr6(timestamp: Long) : Boolean = {
    val uploadDate = new DateTime(timestamp)
    uploadDate.isBefore(DateTime.parse(s"${uploadDate.getYear()}-04-06"))
  }
}
