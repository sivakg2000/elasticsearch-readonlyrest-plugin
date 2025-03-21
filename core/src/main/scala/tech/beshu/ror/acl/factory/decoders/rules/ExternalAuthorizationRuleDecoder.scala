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
package tech.beshu.ror.acl.factory.decoders.rules

import cats.data.NonEmptySet
import cats.implicits._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import io.circe.Decoder
import tech.beshu.ror.acl.domain.{Group, User}
import tech.beshu.ror.acl.orders._
import tech.beshu.ror.acl.blocks.definitions.{CacheableExternalAuthorizationServiceDecorator, ExternalAuthorizationService}
import tech.beshu.ror.acl.blocks.rules.ExternalAuthorizationRule
import tech.beshu.ror.acl.factory.RawRorConfigBasedCoreFactory.AclCreationError
import tech.beshu.ror.acl.factory.RawRorConfigBasedCoreFactory.AclCreationError.Reason.Message
import tech.beshu.ror.acl.factory.RawRorConfigBasedCoreFactory.AclCreationError.RulesLevelCreationError
import tech.beshu.ror.acl.factory.decoders.definitions.Definitions
import tech.beshu.ror.acl.factory.decoders.definitions.ExternalAuthorizationServicesDecoder._
import tech.beshu.ror.acl.factory.decoders.rules.RuleBaseDecoder.RuleDecoderWithoutAssociatedFields
import tech.beshu.ror.acl.utils.CirceOps._

import scala.concurrent.duration.FiniteDuration
import tech.beshu.ror.acl.factory.decoders.common._

class ExternalAuthorizationRuleDecoder(authotizationServices: Definitions[ExternalAuthorizationService])
  extends RuleDecoderWithoutAssociatedFields[ExternalAuthorizationRule](
    ExternalAuthorizationRuleDecoder
      .settingsDecoder(authotizationServices)
      .map(new ExternalAuthorizationRule(_))
  )

object ExternalAuthorizationRuleDecoder {

  private def settingsDecoder(authorizationServices: Definitions[ExternalAuthorizationService]): Decoder[ExternalAuthorizationRule.Settings] = {
    Decoder
      .instance { c =>
        for {
          name <- c.downField("user_groups_provider").as[ExternalAuthorizationService.Name]
          groups <- c.downField("groups").as[NonEmptySet[Group]]
          users <- c.downField("users").as[Option[NonEmptySet[User.Id]]]
          ttl <- c.downFields("cache_ttl_in_sec", "cache_ttl").as[Option[FiniteDuration Refined Positive]]
        } yield (name, ttl, groups, users.getOrElse(NonEmptySet.one(User.Id("*"))))
      }
      .toSyncDecoder
      .mapError(RulesLevelCreationError.apply)
      .emapE {
        case (name, Some(ttl), groups, users) =>
          findAuthorizationService(authorizationServices.items, name)
            .map(new CacheableExternalAuthorizationServiceDecorator(_, ttl))
            .map(ExternalAuthorizationRule.Settings(_, groups, users))
        case (name, None, groups, users) =>
          findAuthorizationService(authorizationServices.items, name)
            .map(ExternalAuthorizationRule.Settings(_, groups, users))
      }
      .decoder
  }

  private def findAuthorizationService(authorizationServices: List[ExternalAuthorizationService],
                                       searchedServiceName: ExternalAuthorizationService.Name): Either[AclCreationError, ExternalAuthorizationService] = {
    authorizationServices.find(_.id === searchedServiceName) match {
      case Some(service) => Right(service)
      case None => Left(RulesLevelCreationError(Message(s"Cannot find user groups provider with name: ${searchedServiceName.show}")))
    }
  }
}