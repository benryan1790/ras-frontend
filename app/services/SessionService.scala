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

package services

import config.{ApplicationConfig, RasShortLivedHttpCaching, SessionCacheWiring}
import models._
import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc.Request
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SessionService extends SessionService {
  override val config: ApplicationConfig = ApplicationConfig
}

object CacheKeys extends Enumeration {
  val All, UserChoice, Name, Nino, Dob, StatusResult, UploadResponse, Envelope, UrBannerDismissed = Value
}

trait SessionService extends SessionCacheWiring {

  val config: ApplicationConfig
  val RAS_SESSION_KEY = "ras_session"
  val cleanMemberName = MemberName("", "")
  val cleanMemberNino = MemberNino("")
  val cleanMemberDateOfBirth = MemberDateOfBirth(RasDate(None, None, None))
  val cleanResidencyStatusResult = ResidencyStatusResult("", None, "", "", "", "", "")
  val cleanSession = RasSession("", cleanMemberName, cleanMemberNino, cleanMemberDateOfBirth, cleanResidencyStatusResult, None)

  def fetchRasSession()(implicit request: Request[_], hc: HeaderCarrier): Future[Option[RasSession]] = {
    sessionCache.fetchAndGetEntry[RasSession](RAS_SESSION_KEY)
  }

  def resetCache(key: CacheKeys.Value)(implicit request: Request[_], hc: HeaderCarrier): Future[Option[RasSession]] = cache(key)

  def cache[T](key: CacheKeys.Value, value: Option[T] = None)(implicit request: Request[_], hc: HeaderCarrier): Future[Option[RasSession]] = {

    val result = fetchRasSession flatMap { currentSession =>

      val session = currentSession.getOrElse(cleanSession)

      sessionCache.cache[RasSession](RAS_SESSION_KEY,
        key match {
          case CacheKeys.UserChoice => session.copy(userChoice = value.getOrElse("").toString)
          case CacheKeys.Name => session.copy(name = value.getOrElse(cleanMemberName).asInstanceOf[MemberName])
          case CacheKeys.Nino => session.copy(nino = value.getOrElse(cleanMemberNino).asInstanceOf[MemberNino])
          case CacheKeys.Dob => session.copy(dateOfBirth = value.getOrElse(cleanMemberDateOfBirth) .asInstanceOf[MemberDateOfBirth])
          case CacheKeys.StatusResult => session.copy(residencyStatusResult = value.getOrElse(cleanResidencyStatusResult).asInstanceOf[ResidencyStatusResult])
          case CacheKeys.UploadResponse => session.copy(uploadResponse = value.asInstanceOf[Option[UploadResponse]])
          case CacheKeys.Envelope => session.copy(envelope = value.asInstanceOf[Option[Envelope]])
          case CacheKeys.UrBannerDismissed => session.copy(urBannerDismissed = value.asInstanceOf[Option[Boolean]])
          case _ => cleanSession.copy(urBannerDismissed = session.urBannerDismissed)
        }
      )
    }

    result.map(cacheMap => {
      cacheMap.getEntry[RasSession](RAS_SESSION_KEY)
    })
  }

  def hasUserDimissedUrBanner()(implicit request: Request[_], hc: HeaderCarrier): Future[Boolean] = {
    config.urBannerEnabled match {
      case true => fetchRasSession flatMap { currentSession =>
        Future.successful(currentSession.flatMap(_.urBannerDismissed).getOrElse(false))
      }
      case false => Future.successful(true)
    }
  }
}

trait ShortLivedCache  {

  val shortLivedCache: ShortLivedHttpCaching = RasShortLivedHttpCaching
  private val source = "ras"

  val hoursToWaitForReUpload = 24

  val STATUS_AVAILABLE: String = "AVAILABLE"

  val defaultDownloadName = "Residency-status"

  def createFileSession(userId: String, envelopeId: String)(implicit hc: HeaderCarrier):Future[Boolean] = {

    shortLivedCache.cache[FileSession](source, userId, "fileSession",
      FileSession(None, None, userId, Some(DateTime.now().getMillis), None)).map(res => true) recover {
      case ex: Throwable => Logger.error(s"unable to create FileSession to cache => " +
        s"${userId} , envelopeId :${envelopeId},  Exception is ${ex.getMessage}")
        false
      //retry or what is the other option?
    }
  }

  def fetchFileSession(userId: String)(implicit hc: HeaderCarrier) = {
    shortLivedCache.fetchAndGetEntry[FileSession](source, userId, "fileSession").recover {
      case ex: Throwable => Logger.error(s"unable to fetch FileSession from cache => " +
        s"${userId} , Exception is ${ex.getMessage}")
        None
    }
  }

  def hasBeen24HoursSinceTheUpload(fileUploadTime: Long) = {
    new DateTime(fileUploadTime).plusHours(hoursToWaitForReUpload).isBefore(DateTime.now.getMillis)
  }

  def failedProcessingUploadedFile(userId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    fetchFileSession(userId).map {
      case Some(fileSession) =>
        fileSession.uploadTimeStamp match {
          case Some(timestamp) =>
            errorInFileUpload(fileSession) || (hasBeen24HoursSinceTheUpload(timestamp) && !fileSession.resultsFile.isDefined)
          case _ =>
            Logger.error("[ShortLivedCache][failedProcessingUploadedFile] no upload timestamp found")
            false
        }
      case _ =>
        Logger.error("[ShortLivedCache][failedProcessingUploadedFile] no file session found")
        false
    }
  }

  def errorInFileUpload(fileSession: FileSession)(implicit hc: HeaderCarrier): Boolean = {
    fileSession.userFile match {
      case Some(userFile) =>
        userFile.status match {
          case STATUS_AVAILABLE => false
          case _ => {
            removeFileSessionFromCache(fileSession.userId)
            true
          }
        }
      case _ => false
    }
  }

  def getDownloadFileName(fileSession: FileSession)(implicit hc: HeaderCarrier): String = {
    val name = fileSession.fileMetadata.flatMap(_.name).getOrElse(defaultDownloadName)
    if (name.indexOf(".") > 0)
      name.take(name.lastIndexOf("."))
    else
      name
  }

  def isFileInProgress(userId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    fetchFileSession(userId).map(fileSession =>
      fileSession.isDefined match {
        case true =>
          fileSession.get.resultsFile.isDefined || !hasBeen24HoursSinceTheUpload(fileSession.get.uploadTimeStamp.get)
        case false =>
          Logger.error("fileSession not defined for " + userId)
          false
    }).recover {
      case ex: Throwable =>
        Logger.error(s"unable to fetch FileSession from cache to check isFileInProgress => ${userId} , Exception is ${ex.getMessage}")
        false
    }
  }
  
  def removeFileSessionFromCache(userId: String)(implicit hc: HeaderCarrier) = {
    shortLivedCache.remove(userId).map(_.status).recover {
    case ex: Throwable => Logger.error(s"unable to remove FileSession from cache  => " +
      s"${userId} , Exception is ${ex.getMessage}")
    //try again as the only option left if sessioncache fails
      shortLivedCache.remove(userId).map(_.status)
    }
  }
}

object ShortLivedCache extends ShortLivedCache {
  override val shortLivedCache: ShortLivedHttpCaching = RasShortLivedHttpCaching
  override val hoursToWaitForReUpload = ApplicationConfig.hoursToWaitForReUpload
}


