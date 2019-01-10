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

package validators



import forms.MemberNameForm.Messages
import models.RasDate
import org.joda.time.DateTime
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

trait DateValidator {

  val YEAR_FIELD_LENGTH: Int = 4
  val year = "year"
  val month = "month"
  val day = "day"

  val rasDateConstraint : Constraint[RasDate] = Constraint("dateOfBirth") ({
    date => {

      val leapYear =
        try{
          new DateTime().withYear(date.year.getOrElse("0").toInt).year.isLeap
        } catch {
          case e:NumberFormatException => false
        }

      if (date.day.isEmpty && date.month.isEmpty && date.year.isEmpty)
        Invalid(Seq(ValidationError(Messages("error.mandatory", Messages("dob")), day)))

      else if (date.day.isEmpty)
        Invalid(Seq(ValidationError(Messages("error.mandatory", Messages(day)), day)))

      else if (date.month.isEmpty)
        Invalid(Seq(ValidationError(Messages("error.mandatory", Messages(month)), month)))

      else if (date.year.isEmpty)
        Invalid(Seq(ValidationError(Messages("error.mandatory", Messages(year)), year)))

      else if (!DateValidator.checkForNumber(date.day.getOrElse("0")))
        Invalid(Seq(ValidationError(Messages("error.date.non.number",Messages(day)), day)))

      else if (!DateValidator.checkForNumber(date.month.getOrElse("0")))
        Invalid(Seq(ValidationError(Messages("error.date.non.number",Messages(month)), month)))

      else if (!DateValidator.checkForNumber(date.year.getOrElse("0")))
        Invalid(Seq(ValidationError(Messages("error.date.non.number",Messages(year)), year)))

      else if (!DateValidator.checkDayRange(date)) {
        if(date.month.getOrElse("0").toInt == 2 && leapYear)
          Invalid(Seq(ValidationError(Messages("error.day.invalid.feb.leap"), day)))
        else if(date.month.getOrElse("0").toInt == 2)
          Invalid(Seq(ValidationError(Messages("error.day.invalid.feb"), day)))
        else if(List(4,6,9,11).contains(date.month.getOrElse("0").toInt))
          Invalid(Seq(ValidationError(Messages("error.day.invalid.thirty"), day)))
        else
          Invalid(Seq(ValidationError(Messages("error.day.invalid"), day)))
      }

      else if (!DateValidator.checkMonthRange(date.month.getOrElse("0")))
        Invalid(Seq(ValidationError(Messages("error.month.invalid"), month)))

      else if (!DateValidator.checkYearLength(date.year.getOrElse("0")))
        Invalid(Seq(ValidationError(Messages("error.year.invalid.format"), year)))

      else {
        try {
          if (date.isInFuture)
            Invalid(Seq(ValidationError(Messages("error.dob.invalid.future"), day)))

          else if (!DateValidator.isAfter1900(date.year.getOrElse("0")))
            Invalid(Seq(ValidationError(Messages("error.dob.before.1900"), year)))
          else
            Valid
        }
        catch {
          // $COVERAGE-OFF$Disabling highlighting by default until a workaround for https://issues.scala-lang.org/browse/SI-8596 is found
          case e: Exception => Valid
          // $COVERAGE-ON$

        }
      }
    }
  })

  def checkForNumber(value: String): Boolean = {
    value forall Character.isDigit
  }

  def checkDayRange(date:RasDate): Boolean = {

    try{

      val day = date.day.getOrElse("0")
      val month = date.month.getOrElse("0")
      val year = date.year.getOrElse("0").toInt
      val leapYear = new DateTime().withYear(year).year.isLeap

      if (day forall Character.isDigit){
        if(month.toInt == 2 && leapYear)
          day.toInt > 0 && day.toInt < 30
        else if(month.toInt == 2)
          day.toInt > 0 && day.toInt < 29
        else if(List(4,6,9,11).contains(month.toInt))
          day.toInt > 0 && day.toInt < 31
        else
          day.toInt > 0 && day.toInt < 32
      }
      else
        false
    }catch {
      case e: NumberFormatException => false
    }

  }

  def checkMonthRange(month: String): Boolean = {
    if (month forall Character.isDigit)
      month.toInt > 0 && month.toInt < 13
    else
      false
  }

  def checkYearLength(year: String): Boolean = {
    if (year forall Character.isDigit)
      year.length == YEAR_FIELD_LENGTH
    else
      false
  }

  def isAfter1900(year: String): Boolean = {
    if (year forall Character.isDigit)
      year.toInt >= 1900
    else
      false
  }
}

object DateValidator extends DateValidator
