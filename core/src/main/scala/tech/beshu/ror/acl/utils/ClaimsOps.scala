/*
 *    This file is part of ReadonlyREST.
 *
 *    ReadonlyREST is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    ReadonlyREST is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with ReadonlyREST.  If not, see http://www.gnu.org/licenses/
 */
package tech.beshu.ror.acl.utils

import eu.timepit.refined.types.string.NonEmptyString
import io.jsonwebtoken.Claims
import org.apache.logging.log4j.scala.Logging
import tech.beshu.ror.acl.domain.{ClaimName, Group, Header, User}
import tech.beshu.ror.acl.utils.ClaimsOps.ClaimSearchResult
import tech.beshu.ror.acl.utils.ClaimsOps.ClaimSearchResult._

import scala.collection.JavaConverters._
import scala.language.{implicitConversions, postfixOps}
import scala.util.Try

class ClaimsOps(val claims: Claims) extends Logging {

  def headerNameClaim(name: Header.Name): ClaimSearchResult[Header] = {
    Option(claims.get(name.value.value, classOf[String]))
      .flatMap(NonEmptyString.unapply) match {
      case Some(headerValue) => Found(Header.apply(name, headerValue))
      case None => NotFound
    }
  }

  def userIdClaim(claimName: ClaimName): ClaimSearchResult[User.Id] = {
    Try(claimName.name.read[Any](claims))
      .map {
        case value: String => Found(User.Id(value))
        case _ => NotFound
      }
      .fold(
        ex => {
          logger.debug("JsonPath reading exception", ex)
          NotFound
        },
        identity
      )
  }

  def groupsClaim(claimName: ClaimName): ClaimSearchResult[Set[Group]] = {
    Try(claimName.name.read[Any](claims))
      .map {
        case value: String =>
          Found((value :: Nil).flatMap(toGroup).toSet)
        case collection: java.util.Collection[_] =>
          Found {
            collection.asScala
              .collect { case value: String => value }
              .flatMap(toGroup)
              .toSet
          }
        case _ =>
          NotFound
      }
      .fold(
        ex => {
          logger.debug("JsonPath reading exception", ex)
          NotFound
        },
        identity
      )
  }

  private def toGroup(value: String) = {
    NonEmptyString.unapply(value).map(Group.apply)
  }
}

object ClaimsOps {
  implicit def toClaimsOps(claims: Claims): ClaimsOps = new ClaimsOps(claims)

  sealed trait ClaimSearchResult[+T]
  object ClaimSearchResult {
    final case class Found[+T](value: T) extends ClaimSearchResult[T]
    case object NotFound extends ClaimSearchResult[Nothing]
  }
}
