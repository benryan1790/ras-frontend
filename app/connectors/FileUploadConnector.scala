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

package connectors

import config.{ApplicationConfig, WSHttp}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext

import scala.concurrent.Future

trait FileUploadConnector extends ServicesConfig {

  val http: HttpPost
  lazy val rasApiBaseUrl = baseUrl("relief-at-source")
  lazy val fileUploadBaseUrl = baseUrl("file-upload")
  lazy val fileUploadUrlSuffix = getString("file-upload-url-suffix")
  val rasFileUploadCallbackUrl: String

  lazy val maxItems = getInt("file-upload-constraints.maxItems")
  lazy val maxSize = getString("file-upload-constraints.maxSize")
  lazy val maxSizePerItem = getString("file-upload-constraints.maxSizePerItem")
  lazy val allowZeroLengthFiles = getBoolean("file-upload-constraints.allowZeroLengthFiles")

  def createEnvelope(userId: String)(implicit hc: HeaderCarrier): Future[HttpResponse] = {


    val requestBody = Json.parse(
      s"""
        {
          "callbackUrl": "$rasApiBaseUrl$rasFileUploadCallbackUrl/${userId}",
          "constraints": 	{
              "maxItems": 1,
              "maxSize": "$maxSize",
              "maxSizePerItem": "$maxSizePerItem",
              "contentTypes": ["text/xml"],
              "allowZeroLengthFiles": $allowZeroLengthFiles
              }
          }
      """.stripMargin)

    http.POST[JsValue, HttpResponse](
      s"$fileUploadBaseUrl/$fileUploadUrlSuffix", requestBody, Seq()
    )(implicitly, implicitly, hc, MdcLoggingExecutionContext.fromLoggingDetails(hc))

  }
}

object FileUploadConnector extends FileUploadConnector {
  override val http = WSHttp
  override lazy val rasFileUploadCallbackUrl: String = ApplicationConfig.fileUploadCallBack
}
