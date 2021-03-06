
package com.lbs.server.service

import java.time.{LocalTime, ZonedDateTime}

import cats.instances.either._
import com.lbs.api.LuxmedApi
import com.lbs.api.json.model._
import com.lbs.server.ThrowableOr
import com.lbs.server.util.ServerModelConverters._
import org.jasypt.util.text.TextEncryptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import scalaj.http.HttpResponse

@Service
class ApiService extends SessionSupport {

  @Autowired
  protected var dataService: DataService = _
  @Autowired
  private var textEncryptor: TextEncryptor = _

  private val luxmedApi = new LuxmedApi[ThrowableOr]

  def getAllCities(accountId: Long): ThrowableOr[List[IdName]] =
    withSession(accountId) { session =>
      luxmedApi.reservationFilter(session.accessToken, session.tokenType).map(_.cities)
    }

  def getAllClinics(accountId: Long, cityId: Long): ThrowableOr[List[IdName]] =
    withSession(accountId) { session =>
      luxmedApi.reservationFilter(session.accessToken,
        session.tokenType, cityId = Some(cityId)).map(_.clinics)
    }

  def getAllServices(accountId: Long, cityId: Long, clinicId: Option[Long]): ThrowableOr[List[IdName]] =
    withSession(accountId) { session =>
      luxmedApi.reservationFilter(session.accessToken,
        session.tokenType, cityId = Some(cityId),
        clinicId = clinicId).map(_.services)
    }

  def getAllDoctors(accountId: Long, cityId: Long, clinicId: Option[Long], serviceId: Long): ThrowableOr[List[IdName]] =
    withSession(accountId) { session =>
      luxmedApi.reservationFilter(session.accessToken,
        session.tokenType, cityId = Some(cityId),
        clinicId = clinicId, serviceId = Some(serviceId)).map(_.doctors)
    }

  def getPayers(accountId: Long, cityId: Long, clinicId: Option[Long], serviceId: Long): ThrowableOr[(Option[IdName], Seq[IdName])] =
    withSession(accountId) { session =>
      val reservationFilterResponse = luxmedApi.reservationFilter(session.accessToken,
        session.tokenType, cityId = Some(cityId),
        clinicId = clinicId, serviceId = Some(serviceId))
      reservationFilterResponse.map { response =>
        response.defaultPayer -> response.payers
      }
    }

  def getAvailableTerms(accountId: Long, payerId: Long, cityId: Long, clinicId: Option[Long], serviceId: Long, doctorId: Option[Long],
                        fromDate: ZonedDateTime = ZonedDateTime.now(), toDate: Option[ZonedDateTime] = None, timeFrom: LocalTime, timeTo: LocalTime,
                        languageId: Long = 10, findFirstFreeTerm: Boolean = false): ThrowableOr[List[AvailableVisitsTermPresentation]] =
    withSession(accountId) { session =>
      val termsEither = luxmedApi.availableTerms(session.accessToken, session.tokenType, payerId, cityId, clinicId, serviceId, doctorId,
        fromDate, toDate, languageId = languageId, findFirstFreeTerm = findFirstFreeTerm).map(_.availableVisitsTermPresentation)
      termsEither.map { terms =>
        terms.filter { term =>
          val time = term.visitDate.startDateTime.toLocalTime
          time == timeFrom || time == timeTo || (time.isAfter(timeFrom) && time.isBefore(timeTo))
        }
      }
    }

  def temporaryReservation(accountId: Long, temporaryReservationRequest: TemporaryReservationRequest, valuationsRequest: ValuationsRequest): ThrowableOr[(TemporaryReservationResponse, ValuationsResponse)] =
    withSession(accountId) { session =>
      for {
        temporaryReservation <- luxmedApi.temporaryReservation(session.accessToken, session.tokenType, temporaryReservationRequest)
        valuationsResponse <- luxmedApi.valuations(session.accessToken, session.tokenType, valuationsRequest)
      } yield temporaryReservation -> valuationsResponse
    }

  def deleteTemporaryReservation(accountId: Long, temporaryReservationId: Long): ThrowableOr[HttpResponse[String]] =
    withSession(accountId) { session =>
      luxmedApi.deleteTemporaryReservation(session.accessToken, session.tokenType, temporaryReservationId)
    }

  def reservation(accountId: Long, reservationRequest: ReservationRequest): ThrowableOr[ReservationResponse] =
    withSession(accountId) { session =>
      luxmedApi.reservation(session.accessToken, session.tokenType, reservationRequest)
    }

  def reserveVisit(accountId: Long, term: AvailableVisitsTermPresentation): ThrowableOr[ReservationResponse] = {
    val temporaryReservationRequest = term.mapTo[TemporaryReservationRequest]
    val valuationsRequest = term.mapTo[ValuationsRequest]
    for {
      okResponse <- temporaryReservation(accountId, temporaryReservationRequest, valuationsRequest)
      (temporaryReservation, valuations) = okResponse
      temporaryReservationId = temporaryReservation.id
      visitTermVariant = valuations.visitTermVariants.head
      reservationRequest = (temporaryReservationId, visitTermVariant, term).mapTo[ReservationRequest]
      reservation <- reservation(accountId, reservationRequest)
    } yield reservation
  }

  def canTermBeChanged(accountId: Long, reservationId: Long): ThrowableOr[HttpResponse[String]] =
    withSession(accountId) { session =>
      luxmedApi.canTermBeChanged(session.accessToken, session.tokenType, reservationId)
    }


  def detailToChangeTerm(accountId: Long, reservationId: Long): ThrowableOr[ChangeTermDetailsResponse] =
    withSession(accountId) { session =>
      luxmedApi.detailToChangeTerm(session.accessToken, session.tokenType, reservationId)
    }

  def temporaryReservationToChangeTerm(accountId: Long, reservationId: Long, temporaryReservationRequest: TemporaryReservationRequest, valuationsRequest: ValuationsRequest): ThrowableOr[(TemporaryReservationResponse, ValuationsResponse)] =
    withSession(accountId) { session =>
      for {
        temporaryReservation <- luxmedApi.temporaryReservationToChangeTerm(session.accessToken, session.tokenType, reservationId, temporaryReservationRequest)
        valuationsResponse <- luxmedApi.valuationToChangeTerm(session.accessToken, session.tokenType, reservationId, valuationsRequest)
      } yield temporaryReservation -> valuationsResponse
    }

  def valuationToChangeTerm(accountId: Long, reservationId: Long, valuationsRequest: ValuationsRequest): ThrowableOr[ValuationsResponse] =
    withSession(accountId) { session =>
      luxmedApi.valuationToChangeTerm(session.accessToken, session.tokenType, reservationId, valuationsRequest)
    }

  def changeTerm(accountId: Long, reservationId: Long, reservationRequest: ReservationRequest): ThrowableOr[ChangeTermResponse] =
    withSession(accountId) { session =>
      luxmedApi.changeTerm(session.accessToken, session.tokenType, reservationId, reservationRequest)
    }

  def updateReservedVisit(accountId: Long, term: AvailableVisitsTermPresentation): ThrowableOr[ChangeTermResponse] = {
    val reservedVisitMaybe = reservedVisits(accountId, toDate = ZonedDateTime.now().plusMonths(6)).map(_.find(_.service.id == term.serviceId))
    reservedVisitMaybe match {
      case Right(Some(reservedVisit: ReservedVisit)) =>
        val reservationId = reservedVisit.reservationId
        val temporaryReservationRequest = term.mapTo[TemporaryReservationRequest]
        val valuationsRequest = term.mapTo[ValuationsRequest]
        val canTermBeChangedResponse = canTermBeChanged(accountId, reservationId)
        if (canTermBeChangedResponse.exists(_.code == 204)) {
          for {
            okResponse <- temporaryReservationToChangeTerm(accountId, reservationId, temporaryReservationRequest, valuationsRequest)
            (temporaryReservation, valuations) = okResponse
            temporaryReservationId = temporaryReservation.id
            visitTermVariant = valuations.visitTermVariants.head
            reservationRequest = (temporaryReservationId, visitTermVariant, term).mapTo[ReservationRequest]
            reservation <- changeTerm(accountId, reservationId, reservationRequest)
          } yield reservation
        } else left(s"Term for reservation [$reservationId] can't be changed")
      case Left(ex) =>
        Left(ex)
      case _ =>
        left(s"Existing reservation for service [${term.serviceId}] not found. Nothing to update")
    }
  }

  def visitsHistory(accountId: Long, fromDate: ZonedDateTime = ZonedDateTime.now().minusYears(1),
                    toDate: ZonedDateTime = ZonedDateTime.now(), page: Int = 1, pageSize: Int = 100): ThrowableOr[List[HistoricVisit]] =
    withSession(accountId) { session =>
      luxmedApi.visitsHistory(session.accessToken, session.tokenType, fromDate, toDate, page, pageSize).map(_.historicVisits)
    }

  def reservedVisits(accountId: Long, fromDate: ZonedDateTime = ZonedDateTime.now(),
                     toDate: ZonedDateTime = ZonedDateTime.now().plusMonths(3)): ThrowableOr[List[ReservedVisit]] =
    withSession(accountId) { session =>
      luxmedApi.reservedVisits(session.accessToken, session.tokenType, fromDate, toDate).map(_.reservedVisits)
    }

  def deleteReservation(accountId: Long, reservationId: Long): ThrowableOr[HttpResponse[String]] =
    withSession(accountId) { session =>
      luxmedApi.deleteReservation(session.accessToken, session.tokenType, reservationId)
    }

  def login(username: String, password: String): ThrowableOr[LoginResponse] = {
    luxmedApi.login(username, textEncryptor.decrypt(password))
  }

  private def left(msg: String) = Left(new RuntimeException(msg))

}
