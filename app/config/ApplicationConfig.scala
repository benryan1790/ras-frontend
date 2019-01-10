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

package config

import play.api.Play.{configuration, current}
import uk.gov.hmrc.play.config.ServicesConfig

import scala.util.Try

trait ApplicationConfig {
  val analyticsToken: String
  val analyticsHost: String
  val reportAProblemPartialUrl: String
  val reportAProblemNonJSUrl: String
  val signOutUrl: String
  val signOutAndContinueUrl: String
  val loginCallback:String
  val fileUploadCallBack: String
  val fileDeletionUrl: String
  val hoursToWaitForReUpload :Int
  val rasApiResidencyStatusEndpoint: String
  val reportAProblemUrl: String
  val rasApiVersion: String
  val timeOutSeconds: Int
  val timeOutCountDownSeconds: Int
  val refreshInterval: Int
  val enableRefresh: Boolean
  val urBannerEnabled: Boolean
  val urBannerLinkUrl: String
}

object ApplicationConfig extends ApplicationConfig with ServicesConfig {

  private def loadConfig(key: String) = configuration.getString(key).getOrElse(throw new Exception(s"Missing configuration key: $key"))

  private val contactHost = configuration.getString("contact-frontend.host").getOrElse("")
  private val contactFormServiceIdentifier = "RAS"
  private val caFrontendHost = configuration.getString("ca-frontend.host").getOrElse("")

  override lazy val analyticsToken = loadConfig(s"google-analytics.token")
  override lazy val analyticsHost = loadConfig(s"google-analytics.host")

  override lazy val hoursToWaitForReUpload = configuration.getInt(s"re-upload.wait.time.hours").getOrElse(
    throw new Exception("Missing configuration key: re-upload.wait.time.hours "))

  private val logoutCallback = configuration.getString("gg-urls.logout-callback.url").getOrElse("/relief-at-source/")
  private val signOutBaseUrl = s"$caFrontendHost/gg/sign-out?continue="
  private val continueCallback: String =  configuration.getString("gg-urls.continue-callback.url").getOrElse("/relief-at-source/")

  override lazy val reportAProblemUrl = s"$contactHost/contact"
  override lazy val reportAProblemPartialUrl = s"$contactHost/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  override lazy val reportAProblemNonJSUrl = s"$contactHost/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  override lazy val signOutUrl = s"$signOutBaseUrl$logoutCallback"
  override lazy val signOutAndContinueUrl = s"$signOutBaseUrl$continueCallback"
  override lazy val loginCallback: String = configuration.getString("gg-urls.login-callback.url").getOrElse("/relief-at-source/")
  override lazy val fileUploadCallBack: String = configuration.getString("file-upload-ras-callback-url")
    .getOrElse(throw new Exception("Missing configuration key: file-upload-ras-callback-url"))
  override lazy val fileDeletionUrl: String = configuration.getString("file-deletion-url").getOrElse("/ras-api/file/remove/")
  override lazy val rasApiResidencyStatusEndpoint: String = getString("residency-status-url")

  override lazy val urBannerEnabled: Boolean = configuration.getBoolean("ur-banner.enabled").getOrElse(false)
  override lazy val urBannerLinkUrl: String = configuration.getString("ur-banner.link-url").getOrElse("")

  override lazy val rasApiVersion: String = getString("ras-api-version")

  override lazy val timeOutSeconds : Int = configuration.getString("sessionTimeout.timeoutSeconds").getOrElse("780").toInt
  override lazy val timeOutCountDownSeconds: Int = configuration.getString("sessionTimeout.time-out-countdown-seconds").getOrElse("120").toInt
  override lazy val refreshInterval: Int = timeOutSeconds + 10
  override lazy val enableRefresh: Boolean= configuration.getBoolean("sessionTimeout.enableRefresh").getOrElse(true)
}
