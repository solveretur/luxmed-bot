
package com.lbs.server

import java.time._
import java.time.format.DateTimeFormatter
import java.util.Locale

import com.lbs.api.json.model._
import com.lbs.bot.model.Command
import com.lbs.common.ModelConverters
import com.lbs.server.conversation.Book.BookingData
import com.lbs.server.conversation.Login.UserId
import com.lbs.server.repository.model.{History, Monitoring}

import scala.language.{higherKinds, implicitConversions}
import scala.util.Try


package object util {

  object ServerModelConverters extends ModelConverters {

    implicit val BookingDataToMonitoringConverter:
      ObjectConverter[(UserId, BookingData), Monitoring] =
      (data: (UserId, BookingData)) => {
        val (userId, bookingData) = data
        Monitoring(
          userId = userId.userId,
          accountId = userId.accountId,
          chatId = userId.source.chatId,
          sourceSystemId = userId.source.sourceSystem.id,
          payerId = bookingData.payerId,
          cityId = bookingData.cityId.id,
          cityName = bookingData.cityId.name,
          clinicId = bookingData.clinicId.optionalId,
          clinicName = bookingData.clinicId.name,
          serviceId = bookingData.serviceId.id,
          serviceName = bookingData.serviceId.name,
          doctorId = bookingData.doctorId.optionalId,
          doctorName = bookingData.doctorId.name,
          dateFrom = bookingData.dateFrom,
          dateTo = bookingData.dateTo,
          timeFrom = bookingData.timeFrom,
          timeTo = bookingData.timeTo,
          autobook = bookingData.autobook,
          rebookIfExists = bookingData.rebookIfExists,
          offset = bookingData.offset
        )
      }

    implicit val AvailableVisitsTermPresentationToTemporaryReservationRequestConverter:
      ObjectConverter[AvailableVisitsTermPresentation, TemporaryReservationRequest] =
      (term: AvailableVisitsTermPresentation) => {
        TemporaryReservationRequest(
          clinicId = term.clinic.id,
          doctorId = term.doctor.id,
          payerDetailsList = term.payerDetailsList,
          referralRequiredByService = term.referralRequiredByService,
          roomId = term.roomId,
          serviceId = term.serviceId,
          startDateTime = term.visitDate.startDateTime
        )
      }

    implicit val TmpReservationIdWithValuationsToReservationRequestConverter:
      ObjectConverter[(Long, VisitTermVariant, AvailableVisitsTermPresentation), ReservationRequest] =
      (any: (Long, VisitTermVariant, AvailableVisitsTermPresentation)) => {
        val (tmpReservationId, valuations, term) = any
        ReservationRequest(
          clinicId = term.clinic.id,
          doctorId = term.doctor.id,
          payerData = valuations.valuationDetail.payerData,
          roomId = term.roomId,
          serviceId = term.serviceId,
          startDateTime = term.visitDate.startDateTime,
          temporaryReservationId = tmpReservationId
        )
      }

    implicit val AvailableVisitsTermPresentationToValuationRequestConverter:
      ObjectConverter[AvailableVisitsTermPresentation, ValuationsRequest] =
      (term: AvailableVisitsTermPresentation) => {
        ValuationsRequest(
          clinicId = term.clinic.id,
          doctorId = term.doctor.id,
          payerDetailsList = term.payerDetailsList,
          referralRequiredByService = term.referralRequiredByService,
          roomId = term.roomId,
          serviceId = term.serviceId,
          startDateTime = term.visitDate.startDateTime
        )
      }

    implicit val HistoryToIdNameConverter: ObjectConverter[History, IdName] =
      (history: History) => IdName(history.id, history.name)
  }

  object MessageExtractors {

    object TextCommand {
      def unapply(cmd: Command): Option[String] = if (cmd.callbackData.isEmpty) cmd.message.text.filter(_.nonEmpty) else None
    }

    object OptionalTextCommand {
      def unapply(cmd: Command): Option[Option[String]] = if (cmd.callbackData.isEmpty) Some(TextCommand.unapply(cmd)) else None
    }

    object CallbackCommand {
      def unapply(cmd: Command): Option[String] = cmd.callbackData
    }

    object BooleanString {
      def unapply(string: String): Option[Boolean] = Try(string.toBoolean).toOption
    }

    object IntString {
      def unapply(string: String): Option[Int] = Try(string.toInt).toOption
    }

    object LongString {
      def unapply(string: String): Option[Long] = Try(string.toLong).toOption
    }

  }

  object DateTimeUtil {
    private val DateFormat: Locale => DateTimeFormatter = locale => DateTimeFormatter.ofPattern("dd MMM yyyy", locale)

    private val DateShortFormat = DateTimeFormatter.ofPattern("dd-MM")

    private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val DateTimeFormat: Locale => DateTimeFormatter = locale => DateTimeFormatter.ofPattern("EEE',' dd MMM yyyy',' HH:mm", locale)

    def formatDate(date: ZonedDateTime, locale: Locale): String = date.format(DateFormat(locale))

    def formatDateShort(date: ZonedDateTime): String = date.format(DateShortFormat)

    def formatTime(time: LocalTime): String = time.format(TimeFormat)

    def formatDateTime(date: ZonedDateTime, locale: Locale): String = date.format(DateTimeFormat(locale))

    private val EpochMinutesTillBeginOf2018: Long = epochMinutes(ZonedDateTime.of(2018, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()))

    def epochMinutes(time: ZonedDateTime): Long = time.toInstant.getEpochSecond / 60

    def minutesSinceBeginOf2018(time: ZonedDateTime): Long = epochMinutes(time) - EpochMinutesTillBeginOf2018

    def applyDayMonth(dayMonthStr: String, date: ZonedDateTime): ZonedDateTime = {
      val dayMonth = MonthDay.parse(dayMonthStr, DateShortFormat)
      val newDate = date.withDayOfMonth(dayMonth.getDayOfMonth).withMonth(dayMonth.getMonthValue)

      if (newDate.isBefore(date)) newDate.plusYears(1) else newDate
    }

    def applyHourMinute(hourMinuteStr: String): LocalTime = {
      LocalTime.parse(hourMinuteStr, TimeFormat)
    }
  }

}
